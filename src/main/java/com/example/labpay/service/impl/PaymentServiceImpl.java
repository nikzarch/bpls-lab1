package com.example.labpay.service.impl;

import com.example.labpay.domain.BankOperation;
import com.example.labpay.domain.BankOperationStatus;
import com.example.labpay.domain.BankOperationType;
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
import com.example.labpay.exception.BankTimeoutException;
import com.example.labpay.exception.BankUnavailableException;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.mq.EventPublisher;
import com.example.labpay.mq.events.BitrixDealSyncEvent;
import com.example.labpay.mq.events.WebhookEvent;
import com.example.labpay.repository.BankCardRepository;
import com.example.labpay.repository.BankOperationRepository;
import com.example.labpay.repository.PaymentOrderRepository;
import com.example.labpay.repository.ProductOfferRepository;
import com.example.labpay.repository.WidgetRepository;
import com.example.labpay.service.*;
import com.example.labpay.service.dto.BankChargeResult;
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
    private final BankOperationRepository bankOperationRepository;
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
        XmlAppUser buyer = userService.getByUsername(username);
        PaymentOrder order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (!order.getBuyerId().equals(buyer.getId())) {
            throw new BusinessException("Order does not belong to user");
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException("Order already processed");
        }

        return switch (request.method()) {
            case WALLET -> processWalletPayment(buyer, order);
            case CARD -> processCardPayment(buyer, order, request);
        };
    }

    private PaymentOrderResponse processWalletPayment(XmlAppUser buyer, PaymentOrder order) {
        AtomicReference<WebhookEvent> webhookRef = new AtomicReference<>();
        AtomicReference<BitrixDealSyncEvent> bitrixRef = new AtomicReference<>();

        PaymentOrder result = transactionManagerFacade.execute(
                TransactionOptions.defaults("wallet-payment-transaction"),
                () -> {
                    Widget widget = order.getProduct().getWidget();

                    String holdRef = "order-" + order.getExternalOrderId();
                    walletService.placeHold(buyer.getId(), order.getAmount(), holdRef,
                            "Hold for order " + order.getExternalOrderId());
                    walletService.captureHold(holdRef, TransactionType.WIDGET_PAYMENT_OUT);

                    walletService.credit(
                            widget.getMerchantId(),
                            order.getAmount(),
                            UUID.randomUUID().toString(),
                            "Income from order " + order.getExternalOrderId(),
                            TransactionType.WIDGET_PAYMENT_IN
                    );

                    order.setStatus(OrderStatus.PAID);
                    order.setPaidAt(Instant.now());
                    PaymentOrder saved = orderRepository.save(order);

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
                            buyer.getUsername(),
                            widget.getId(),
                            widget.getMerchantId(),
                            saved.getProduct().getTitle(),
                            saved.getAmount(),
                            saved.getStatus().name(),
                            saved.getPaidAt()
                    ));
                    return saved;
                },
                committed -> log.info("Wallet payment {} committed", committed.getId()),
                ex -> log.error("Wallet payment failed for user {}: {}", buyer.getUsername(), ex.getMessage())
        );

        if (bitrixRef.get() != null) {
            try { bitrixCrmService.syncPaidOrder(bitrixRef.get()); } catch (Exception e) { log.warn("Bitrix sync failed: {}", e.getMessage()); }
        }
        if (webhookRef.get() != null) eventPublisher.publishWebhook(webhookRef.get());

        return toResponse(result);
    }

    private PaymentOrderResponse processCardPayment(XmlAppUser buyer, PaymentOrder order, ProcessPaymentRequest request) {
        if (request.cardToken() == null || request.cardToken().isBlank()) {
            throw new BusinessException("Card token required for card payment");
        }
        BankCard card = bankCardRepository.findByToken(request.cardToken())
                .filter(c -> c.getUserId().equals(buyer.getId()))
                .orElseThrow(() -> new NotFoundException("Card not found"));
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessException("Card is not active");
        }

        String correlationId = UUID.randomUUID().toString();

        transactionManagerFacade.execute(
                TransactionOptions.defaults("card-payment-record"),
                () -> {
                    order.setStatus(OrderStatus.CHARGING);
                    order.setPaymentCorrelationId(correlationId);
                    orderRepository.save(order);

                    bankOperationRepository.save(BankOperation.builder()
                            .correlationId(correlationId)
                            .type(BankOperationType.CARD_PAYMENT)
                            .status(BankOperationStatus.PREPARING)
                            .userId(buyer.getId())
                            .relatedOrderId(order.getId())
                            .cardToken(card.getToken())
                            .maskedCard(card.getMaskedCardNumber())
                            .amount(order.getAmount())
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .attempts(0)
                            .build());
                    return null;
                },
                null,
                ex -> log.error("Failed to record card payment intent [{}]: {}", correlationId, ex.getMessage())
        );

        String cardNumber = cardTokenizer.decrypt(card.getEncryptedCardNumber());

        try {
            bankClient.directCharge(correlationId, cardNumber, order.getAmount().doubleValue());
        } catch (BankTimeoutException e) {
            log.warn("Card charge timed out [order={} corr={}]", order.getId(), correlationId);
            markOrderPendingReconcile(order.getId(), correlationId, "DIRECT_CHARGE timeout");
            PaymentOrder pending = orderRepository.findById(order.getId()).orElseThrow();
            return toResponse(pending);
        } catch (BankUnavailableException e) {
            log.warn("Card charge unavailable [order={} corr={}]", order.getId(), correlationId);
            markOrderFailed(order.getId(), correlationId, "Bank unavailable");
            throw e;
        } catch (BusinessException e) {
            markOrderFailed(order.getId(), correlationId, e.getMessage());
            throw e;
        }

        AtomicReference<WebhookEvent> webhookRef = new AtomicReference<>();
        AtomicReference<BitrixDealSyncEvent> bitrixRef = new AtomicReference<>();

        PaymentOrder finalized = transactionManagerFacade.execute(
                TransactionOptions.defaults("card-payment-settle"),
                () -> {
                    PaymentOrder current = orderRepository.findById(order.getId()).orElseThrow();
                    Widget widget = current.getProduct().getWidget();

                    walletService.credit(
                            widget.getMerchantId(),
                            current.getAmount(),
                            correlationId,
                            "Card payment for order " + current.getExternalOrderId(),
                            TransactionType.WIDGET_PAYMENT_IN
                    );

                    current.setStatus(OrderStatus.PAID);
                    current.setPaidAt(Instant.now());
                    PaymentOrder saved = orderRepository.save(current);

                    BankOperation op = bankOperationRepository.findByCorrelationId(correlationId).orElseThrow();
                    op.setStatus(BankOperationStatus.COMMITTED);
                    op.setUpdatedAt(Instant.now());
                    bankOperationRepository.save(op);

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
                            buyer.getUsername(),
                            widget.getId(),
                            widget.getMerchantId(),
                            saved.getProduct().getTitle(),
                            saved.getAmount(),
                            saved.getStatus().name(),
                            saved.getPaidAt()
                    ));
                    return saved;
                },
                committed -> log.info("Card payment {} committed", committed.getId()),
                ex -> log.error("Card payment settle rolled back for user {}: {}", buyer.getUsername(), ex.getMessage())
        );

        if (bitrixRef.get() != null) {
            try { bitrixCrmService.syncPaidOrder(bitrixRef.get()); } catch (Exception e) { log.warn("Bitrix sync failed: {}", e.getMessage()); }
        }
        if (webhookRef.get() != null) eventPublisher.publishWebhook(webhookRef.get());

        return toResponse(finalized);
    }

    private void markOrderPendingReconcile(Long orderId, String correlationId, String error) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("order-mark-pending"),
                () -> {
                    PaymentOrder o = orderRepository.findById(orderId).orElseThrow();
                    o.setStatus(OrderStatus.PENDING_RECONCILE);
                    orderRepository.save(o);

                    BankOperation op = bankOperationRepository.findByCorrelationId(correlationId).orElseThrow();
                    op.setStatus(BankOperationStatus.PENDING_RECONCILE);
                    op.setUpdatedAt(Instant.now());
                    op.setLastError(error);
                    return bankOperationRepository.save(op);
                },
                null, null
        );
    }

    private void markOrderFailed(Long orderId, String correlationId, String error) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("order-mark-failed"),
                () -> {
                    PaymentOrder o = orderRepository.findById(orderId).orElseThrow();
                    o.setStatus(OrderStatus.FAILED);
                    orderRepository.save(o);

                    BankOperation op = bankOperationRepository.findByCorrelationId(correlationId).orElseThrow();
                    op.setStatus(BankOperationStatus.FAILED);
                    op.setUpdatedAt(Instant.now());
                    op.setLastError(error);
                    return bankOperationRepository.save(op);
                },
                null, null
        );
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