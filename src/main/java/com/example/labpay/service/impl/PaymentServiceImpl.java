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
import com.example.labpay.transaction.TransactionManagerFacade;
import com.example.labpay.transaction.TransactionOptions;
import com.example.labpay.util.CardTokenizer;
import com.example.labpay.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private final TransactionManagerFacade transactionManagerFacade;

    @Value("${app.webhook.secret:webhook-secret-key}")
    private String webhookSecret;

    @Override
    public PaymentOrderResponse createOrder(String username, CreatePaymentRequest request) {
        PaymentOrder order = transactionManagerFacade.execute(
                TransactionOptions.defaults("create-payment-order-transaction"),
                () -> {
                    AppUser buyer = userService.getByUsername(username);

                    Widget widget = widgetRepository.findById(request.widgetId())
                            .orElseThrow(() -> new NotFoundException("Widget not found"));

                    ProductOffer product = productRepository.findById(request.productId())
                            .orElseThrow(() -> new NotFoundException("Product not found"));

                    if (!product.getWidget().getId().equals(widget.getId())) {
                        throw new BusinessException("Product does not belong to widget");
                    }

                    return orderRepository.save(PaymentOrder.builder()
                            .product(product)
                            .buyer(buyer)
                            .status(OrderStatus.CREATED)
                            .amount(product.getPrice().setScale(2, RoundingMode.HALF_UP))
                            .externalOrderId(UUID.randomUUID().toString())
                            .createdAt(Instant.now())
                            .build());
                },
                committedOrder -> log.info("Order {} committed", committedOrder.getId()),
                ex -> log.error("Create order rolled back for user {}: {}", username, ex.getMessage())
        );

        return toResponse(order);
    }

    @Override
    public PaymentOrderResponse processPayment(String username, ProcessPaymentRequest request) {
        PaymentOrder order = transactionManagerFacade.execute(
                TransactionOptions.defaults("process-payment-transaction"),
                () -> {
                    AppUser buyer = userService.getByUsername(username);
                    PaymentOrder currentOrder = orderRepository.findById(request.orderId())
                            .orElseThrow(() -> new NotFoundException("Order not found"));

                    if (!currentOrder.getBuyer().getId().equals(buyer.getId())) {
                        throw new BusinessException("Order does not belong to user");
                    }
                    if (currentOrder.getStatus() != OrderStatus.CREATED) {
                        throw new BusinessException("Order already processed");
                    }

                    String opId = UUID.randomUUID().toString();
                    Widget widget = currentOrder.getProduct().getWidget();

                    switch (request.method()) {
                        case WALLET -> walletService.debit(
                                buyer.getId(),
                                currentOrder.getAmount(),
                                opId,
                                "Payment for order " + currentOrder.getExternalOrderId(),
                                TransactionType.WIDGET_PAYMENT_OUT
                        );
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
                            bankClient.directCharge(cardNumber, currentOrder.getAmount().doubleValue());
                        }
                    }

                    walletService.credit(
                            widget.getMerchant().getId(),
                            currentOrder.getAmount(),
                            opId,
                            "Income from order " + currentOrder.getExternalOrderId(),
                            TransactionType.WIDGET_PAYMENT_IN
                    );

                    currentOrder.setStatus(OrderStatus.PAID);
                    currentOrder.setPaidAt(Instant.now());

                    return orderRepository.save(currentOrder);
                },
                committedOrder -> sendWebhook(committedOrder, committedOrder.getProduct().getWidget()),
                ex -> log.error("Process payment rolled back for user {}: {}", username, ex.getMessage())
        );

        return toResponse(order);
    }

    @Override
    public PaymentOrderResponse getOrder(String username, Long orderId) {
        AppUser user = userService.getByUsername(username);
        PaymentOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getBuyer().getId().equals(user.getId())) {
            throw new BusinessException("You are not the buyer of this order");
        }

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
        return new PaymentOrderResponse(
                o.getId(),
                o.getExternalOrderId(),
                o.getStatus(),
                o.getAmount().setScale(2, RoundingMode.HALF_UP),
                o.getProduct().getTitle(),
                o.getCreatedAt(),
                o.getPaidAt()
        );
    }
}