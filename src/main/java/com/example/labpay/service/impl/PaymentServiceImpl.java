package com.example.labpay.service.impl;

import com.example.labpay.domain.OrderStatus;
import com.example.labpay.domain.PaymentOrder;
import com.example.labpay.domain.card.BankCard;
import com.example.labpay.domain.card.CardStatus;
import com.example.labpay.domain.wallet.TransactionType;
import com.example.labpay.domain.widget.ProductOffer;
import com.example.labpay.domain.widget.Widget;
import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.response.PaymentOrderResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.mq.EventPublisher;
import com.example.labpay.mq.events.BitrixDealSyncEvent;
import com.example.labpay.mq.events.WebhookEvent;
import com.example.labpay.repository.BankCardRepository;
import com.example.labpay.repository.PaymentOrderRepository;
import com.example.labpay.repository.ProductOfferRepository;
import com.example.labpay.repository.WidgetRepository;
import com.example.labpay.service.*;
import com.example.labpay.transaction.TransactionManagerFacade;
import com.example.labpay.transaction.TransactionOptions;
import com.example.labpay.util.CardTokenizer;
import com.example.labpay.xml.XmlAppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    private final EventPublisher eventPublisher;
    private final BitrixCrmService bitrixCrmService;

    @Override
    public PaymentOrderResponse createOrder(String username, CreatePaymentRequest request) {
        PaymentOrder order = transactionManagerFacade.execute(
                TransactionOptions.defaults("create-payment-order-transaction"),
                () -> {
                    XmlAppUser buyer = userService.getByUsername(username);

                    Widget widget = widgetRepository.findById(request.widgetId())
                            .orElseThrow(() -> new NotFoundException("Widget not found"));

                    ProductOffer product = productRepository.findById(request.productId())
                            .orElseThrow(() -> new NotFoundException("Product not found"));

                    if (!product.getWidget().getId().equals(widget.getId())) {
                        throw new BusinessException("Product does not belong to widget");
                    }

                    return orderRepository.save(PaymentOrder.builder()
                            .product(product)
                            .buyerId(buyer.getId())
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
        AtomicReference<WebhookEvent> webhookRef = new AtomicReference<>();
        AtomicReference<BitrixDealSyncEvent> bitrixRef = new AtomicReference<>();
        PaymentOrder order = transactionManagerFacade.execute(
                TransactionOptions.defaults("process-payment-transaction"),
                () -> {
                    XmlAppUser buyer = userService.getByUsername(username);
                    PaymentOrder currentOrder = orderRepository.findById(request.orderId())
                            .orElseThrow(() -> new NotFoundException("Order not found"));

                    if (!currentOrder.getBuyerId().equals(buyer.getId())) {
                        throw new BusinessException("Order does not belong to user");
                    }
                    if (currentOrder.getStatus() != OrderStatus.CREATED) {
                        throw new BusinessException("Order already processed");
                    }

                    String opId = UUID.randomUUID().toString();
                    Widget widget = currentOrder.getProduct().getWidget();

                    switch (request.method()) {
                        case WALLET -> {
                            String holdRef = "order-" + currentOrder.getExternalOrderId();
                            walletService.placeHold(
                                    buyer.getId(),
                                    currentOrder.getAmount(),
                                    holdRef,
                                    "Hold for order " + currentOrder.getExternalOrderId()
                            );
                            walletService.captureHold(holdRef, TransactionType.WIDGET_PAYMENT_OUT);
                        }
                        case CARD -> {
                            if (request.cardToken() == null || request.cardToken().isBlank()) {
                                throw new BusinessException("Card token required for card payment");
                            }

                            BankCard card = bankCardRepository.findByToken(request.cardToken())
                                    .filter(c -> c.getUserId().equals(buyer.getId()))
                                    .orElseThrow(() -> new NotFoundException("Card not found"));

                            if (card.getStatus() != CardStatus.ACTIVE) {
                                throw new BusinessException("Card is not active");
                            }

                            String cardNumber = cardTokenizer.decrypt(card.getEncryptedCardNumber());
                            bankClient.directCharge(cardNumber, currentOrder.getAmount().doubleValue());
                        }
                    }

                    walletService.credit(
                            widget.getMerchantId(),
                            currentOrder.getAmount(),
                            opId,
                            "Income from order " + currentOrder.getExternalOrderId(),
                            TransactionType.WIDGET_PAYMENT_IN
                    );

                    currentOrder.setStatus(OrderStatus.PAID);
                    currentOrder.setPaidAt(Instant.now());
                    PaymentOrder saved = orderRepository.save(currentOrder);

                    webhookRef.set(new WebhookEvent(
                            saved.getExternalOrderId(),
                            widget.getCallbackUrl(),
                            saved.getStatus().name(),
                            saved.getAmount(),
                            0
                    ));

                    bitrixRef.set(new BitrixDealSyncEvent(
                            saved.getId(),
                            saved.getExternalOrderId(),
                            username,
                            widget.getId(),
                            widget.getMerchantId(),
                            saved.getProduct().getTitle(),
                            saved.getAmount(),
                            saved.getStatus().name(),
                            saved.getPaidAt()
                    ));

                    return saved;
                },
                committedOrder -> {
                    log.info("Payment {} committed and async events enqueued", committedOrder.getId());
                    bitrixCrmService.syncPaidOrder(bitrixRef.get());
                },
                ex -> log.error("Process payment rolled back for user {}: {}", username, ex.getMessage())
        );

        return toResponse(order);
    }

    @Override
    public PaymentOrderResponse getOrder(String username, Long orderId) {
        XmlAppUser user = userService.getByUsername(username);
        PaymentOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getBuyerId().equals(user.getId())) {
            throw new BusinessException("You are not the buyer of this order");
        }

        return toResponse(order);
    }

    @Override
    public List<PaymentOrderResponse> getUserOrders(String username) {
        XmlAppUser user = userService.getByUsername(username);
        return orderRepository.findByBuyerId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
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