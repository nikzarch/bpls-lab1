package com.example.labpay.service.impl;

import com.example.labpay.domain.BankOperation;
import com.example.labpay.domain.BankOperationStatus;
import com.example.labpay.domain.BankOperationType;
import com.example.labpay.domain.OrderStatus;
import com.example.labpay.domain.PaymentOrder;
import com.example.labpay.domain.widget.Widget;
import com.example.labpay.domain.wallet.TransactionType;
import com.example.labpay.exception.BankTimeoutException;
import com.example.labpay.exception.BankUnavailableException;
import com.example.labpay.mq.EventPublisher;
import com.example.labpay.mq.events.BitrixDealSyncEvent;
import com.example.labpay.mq.events.WebhookEvent;
import com.example.labpay.repository.BankOperationRepository;
import com.example.labpay.repository.PaymentOrderRepository;
import com.example.labpay.service.BankClient;
import com.example.labpay.service.BitrixCrmService;
import com.example.labpay.service.ReconciliationService;
import com.example.labpay.service.WalletService;
import com.example.labpay.service.dto.BankChargeResult;
import com.example.labpay.transaction.TransactionManagerFacade;
import com.example.labpay.transaction.TransactionOptions;
import com.example.labpay.xml.XmlAppUser;
import com.example.labpay.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final BankClient bankClient;
    private final BankOperationRepository bankOperationRepository;
    private final PaymentOrderRepository orderRepository;
    private final WalletService walletService;
    private final AppUserRepository appUserRepository;
    private final EventPublisher eventPublisher;
    private final BitrixCrmService bitrixCrmService;
    private final TransactionManagerFacade transactionManagerFacade;

    @Value("${app.batch.bank-reconciliation.max-attempts:30}")
    private int maxAttempts;

    @Override
    public void reconcile(BankOperation op) {
        log.info("Reconciling bank op corr={} type={} status={} attempts={}",
                op.getCorrelationId(), op.getType(), op.getStatus(), op.getAttempts());

        try {
            switch (op.getStatus()) {
                case PENDING_RECONCILE -> reconcilePending(op);
                case PENDING_FINALIZE -> reconcileFinalize(op);
                default -> log.debug("Skipping op {} in status {}", op.getCorrelationId(), op.getStatus());
            }
        } catch (BankTimeoutException | BankUnavailableException e) {
            bumpAttempts(op, "Bank unreachable: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected reconcile failure for {}: {}", op.getCorrelationId(), e.getMessage(), e);
            bumpAttempts(op, e.getMessage());
        }
    }

    private void reconcilePending(BankOperation op) {
        BankChargeResult status = bankClient.getChargeStatus(op.getCorrelationId());
        log.info("Bank status for {} = {}", op.getCorrelationId(), status.status());

        switch (status.status()) {
            case "CHARGED" -> applyCommitted(op);
            case "PREPARED" -> {
                if (op.getAttempts() + 1 >= maxAttempts) {
                    log.warn("Max attempts reached for {}, forcing rollback", op.getCorrelationId());
                    try { bankClient.rollbackCharge(op.getCorrelationId()); } catch (Exception ignored) {}
                    markFailed(op, "Max reconcile attempts reached");
                } else {
                    bumpAttempts(op, "Still PREPARED on bank");
                }
            }
            case "REFUNDED", "FAILED", "NOT_FOUND" -> markFailed(op, "Bank status: " + status.status());
            default -> bumpAttempts(op, "Unknown bank status: " + status.status());
        }
    }

    private void reconcileFinalize(BankOperation op) {
        try {
            bankClient.commitCharge(op.getCorrelationId());
            transactionManagerFacade.execute(
                    TransactionOptions.defaults("reconcile-finalize-committed"),
                    () -> {
                        BankOperation fresh = bankOperationRepository.findByCorrelationId(op.getCorrelationId()).orElseThrow();
                        fresh.setStatus(BankOperationStatus.COMMITTED);
                        fresh.setUpdatedAt(Instant.now());
                        return bankOperationRepository.save(fresh);
                    },
                    null, null
            );
        } catch (Exception e) {
            bumpAttempts(op, "Commit retry failed: " + e.getMessage());
        }
    }

    private void applyCommitted(BankOperation op) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("reconcile-apply-committed"),
                () -> {
                    BankOperation fresh = bankOperationRepository.findByCorrelationId(op.getCorrelationId()).orElseThrow();

                    if (fresh.getStatus() == BankOperationStatus.COMMITTED) {
                        return fresh;
                    }

                    if (fresh.getType() == BankOperationType.WALLET_TOPUP) {
                        walletService.credit(
                                fresh.getUserId(),
                                fresh.getAmount(),
                                fresh.getCorrelationId(),
                                "Top-up reconciled from card " + fresh.getMaskedCard(),
                                TransactionType.WALLET_TOP_UP
                        );
                    } else if (fresh.getType() == BankOperationType.CARD_PAYMENT && fresh.getRelatedOrderId() != null) {
                        PaymentOrder order = orderRepository.findById(fresh.getRelatedOrderId()).orElseThrow();
                        if (order.getStatus() != OrderStatus.PAID) {
                            Widget widget = order.getProduct().getWidget();
                            walletService.credit(
                                    widget.getMerchantId(),
                                    order.getAmount(),
                                    fresh.getCorrelationId(),
                                    "Card payment reconciled for order " + order.getExternalOrderId(),
                                    TransactionType.WIDGET_PAYMENT_IN
                            );
                            order.setStatus(OrderStatus.PAID);
                            order.setPaidAt(Instant.now());
                            orderRepository.save(order);
                        }
                    }

                    fresh.setStatus(BankOperationStatus.COMMITTED);
                    fresh.setUpdatedAt(Instant.now());
                    return bankOperationRepository.save(fresh);
                },
                committed -> log.info("Reconciled {} as COMMITTED", op.getCorrelationId()),
                ex -> log.error("Failed to apply committed reconciliation for {}: {}", op.getCorrelationId(), ex.getMessage())
        );

        if (op.getType() == BankOperationType.CARD_PAYMENT && op.getRelatedOrderId() != null) {
            PaymentOrder order = orderRepository.findById(op.getRelatedOrderId()).orElse(null);
            if (order != null && order.getStatus() == OrderStatus.PAID) {
                Widget widget = order.getProduct().getWidget();
                eventPublisher.publishWebhook(new WebhookEvent(
                        order.getExternalOrderId(),
                        widget.getCallbackUrl(),
                        order.getStatus().name(),
                        order.getAmount(),
                        0
                ));
                XmlAppUser buyer = appUserRepository.findById(order.getBuyerId()).orElse(null);
                if (buyer != null) {
                    try {
                        bitrixCrmService.syncPaidOrder(new BitrixDealSyncEvent(
                                order.getId(),
                                order.getExternalOrderId(),
                                buyer.getUsername(),
                                widget.getId(),
                                widget.getMerchantId(),
                                order.getProduct().getTitle(),
                                order.getAmount(),
                                order.getStatus().name(),
                                order.getPaidAt()
                        ));
                    } catch (Exception e) {
                        log.warn("Bitrix sync after reconcile failed: {}", e.getMessage());
                    }
                }
            }
        }
    }

    private void markFailed(BankOperation op, String error) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("reconcile-mark-failed"),
                () -> {
                    BankOperation fresh = bankOperationRepository.findByCorrelationId(op.getCorrelationId()).orElseThrow();
                    fresh.setStatus(BankOperationStatus.FAILED);
                    fresh.setUpdatedAt(Instant.now());
                    fresh.setLastError(error);

                    if (fresh.getType() == BankOperationType.CARD_PAYMENT && fresh.getRelatedOrderId() != null) {
                        PaymentOrder order = orderRepository.findById(fresh.getRelatedOrderId()).orElse(null);
                        if (order != null && order.getStatus() != OrderStatus.PAID) {
                            order.setStatus(OrderStatus.FAILED);
                            orderRepository.save(order);
                        }
                    }
                    return bankOperationRepository.save(fresh);
                },
                null, null
        );
    }

    private void bumpAttempts(BankOperation op, String error) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("reconcile-bump"),
                () -> {
                    BankOperation fresh = bankOperationRepository.findByCorrelationId(op.getCorrelationId()).orElseThrow();
                    fresh.setAttempts(fresh.getAttempts() + 1);
                    fresh.setUpdatedAt(Instant.now());
                    fresh.setLastError(error);
                    return bankOperationRepository.save(fresh);
                },
                null, null
        );
    }
}