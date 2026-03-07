package com.example.labpay.service.impl;

import com.example.labpay.domain.OrderStatus;
import com.example.labpay.domain.PaymentOrder;
import com.example.labpay.domain.card.BankCard;
import com.example.labpay.domain.card.CardStatus;
import com.example.labpay.domain.user.AppUser;
import com.example.labpay.domain.wallet.TransactionType;
import com.example.labpay.domain.widget.ProductOffer;
import com.example.labpay.domain.widget.Widget;
import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.response.PaymentOrderResponse;
import com.example.labpay.dto.response.WebhookPayload;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.repository.BankCardRepository;
import com.example.labpay.repository.PaymentOrderRepository;
import com.example.labpay.repository.ProductOfferRepository;
import com.example.labpay.repository.WidgetRepository;
import com.example.labpay.service.BankClient;
import com.example.labpay.service.PaymentService;
import com.example.labpay.service.UserService;
import com.example.labpay.service.WalletService;
import com.example.labpay.util.CardTokenizer;
import com.example.labpay.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderRepository orderRepository;
    private final ProductOfferRepository productRepository;
    private final WidgetRepository widgetRepository;
    private final BankCardRepository bankCardRepository;
    private final UserService userService;
    private final WalletService walletService;
    private final BankClient bankClient;
    private final CardTokenizer cardTokenizer;

    @Value("${app.webhook.secret:webhook-secret-key}")
    private String webhookSecret;

    @Override
    @Transactional
    public PaymentOrderResponse createOrder(String username, CreatePaymentRequest request) {
        AppUser buyer = userService.getByUsername(username);

        Widget widget = widgetRepository.findById(request.widgetId())
                .orElseThrow(() -> new NotFoundException("Widget not found"));

        ProductOffer product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Product not found"));

        if (!product.getWidget().getId().equals(widget.getId())) {
            throw new BusinessException("Product does not belong to widget");
        }

        PaymentOrder order = orderRepository.save(PaymentOrder.builder()
                .product(product)
                .buyer(buyer)
                .status(OrderStatus.CREATED)
                .amount(product.getPrice().setScale(2, RoundingMode.HALF_UP))
                .externalOrderId(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .build());

        return toResponse(order);
    }

    @Override
    @Transactional
    public PaymentOrderResponse processPayment(String username, ProcessPaymentRequest request) {
        AppUser buyer = userService.getByUsername(username);
        PaymentOrder order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new BusinessException("Order does not belong to user");
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException("Order already processed");
        }

        String opId = UUID.randomUUID().toString();
        Widget widget = order.getProduct().getWidget();

        switch (request.method()) {
            case WALLET -> {
                walletService.debit(buyer.getId(), order.getAmount(), opId,
                        "Payment for order " + order.getExternalOrderId(), TransactionType.WIDGET_PAYMENT_OUT);
            }
            case CARD -> {
                if (request.cardToken() == null || request.cardToken().isBlank()) {
                    throw new BusinessException("Card token required for card payment");
                }
                BankCard card = bankCardRepository.findByToken(request.cardToken())
                        .filter(c -> c.getOwner().getId().equals(buyer.getId()))
                        .orElseThrow(() -> new NotFoundException("Card not found"));

                if (card.getStatus() != CardStatus.ACTIVE) {
                    throw new BusinessException("Card is not active");
                }

                String cardNumber = cardTokenizer.decrypt(card.getEncryptedCardNumber());
                bankClient.directCharge(cardNumber, order.getAmount().doubleValue());
            }
        }

        walletService.credit(widget.getMerchant().getId(), order.getAmount(), opId,
                "Income from order " + order.getExternalOrderId(), TransactionType.WIDGET_PAYMENT_IN);

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        sendWebhook(order, widget);

        return toResponse(order);
    }

    @Override
    public PaymentOrderResponse getOrder(Long orderId) {
        PaymentOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return toResponse(order);
    }

    @Override
    public List<PaymentOrderResponse> getUserOrders(String username) {
        AppUser user = userService.getByUsername(username);
        return orderRepository.findByBuyerId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private void sendWebhook(PaymentOrder order, Widget widget) {
        String data = order.getExternalOrderId() + ":" + order.getAmount().toPlainString() + ":" + order.getStatus().name();
        String signature = HmacUtil.sign(data, webhookSecret);

        WebhookPayload payload = new WebhookPayload(
                order.getExternalOrderId(),
                order.getStatus().name(),
                order.getAmount(),
                signature
        );

        log.info("Webhook sent to {}: {}", widget.getCallbackUrl(), payload);
    }

    private PaymentOrderResponse toResponse(PaymentOrder o) {
        return new PaymentOrderResponse(o.getId(), o.getExternalOrderId(), o.getStatus(),
                o.getAmount().setScale(2, RoundingMode.HALF_UP), o.getProduct().getTitle(), o.getCreatedAt(), o.getPaidAt());
    }
}