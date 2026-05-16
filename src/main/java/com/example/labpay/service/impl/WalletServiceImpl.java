package com.example.labpay.service.impl;

import com.example.labpay.domain.BankOperation;
import com.example.labpay.domain.BankOperationStatus;
import com.example.labpay.domain.BankOperationType;
import com.example.labpay.domain.card.BankCard;
import com.example.labpay.domain.card.CardStatus;
import com.example.labpay.domain.wallet.*;
import com.example.labpay.dto.request.TopUpRequest;
import com.example.labpay.dto.response.TopUpResultResponse;
import com.example.labpay.dto.response.TransactionResponse;
import com.example.labpay.dto.response.WalletResponse;
import com.example.labpay.exception.BankTimeoutException;
import com.example.labpay.exception.BankUnavailableException;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.repository.*;
import com.example.labpay.service.BankClient;
import com.example.labpay.service.UserService;
import com.example.labpay.service.WalletService;
import com.example.labpay.service.dto.BankChargeResult;
import com.example.labpay.transaction.TransactionManagerFacade;
import com.example.labpay.transaction.TransactionOptions;
import com.example.labpay.util.CardTokenizer;
import com.example.labpay.xml.XmlAppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(15);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletHoldRepository walletHoldRepository;
    private final BankCardRepository bankCardRepository;
    private final BankOperationRepository bankOperationRepository;
    private final UserService userService;
    private final BankClient bankClient;
    private final CardTokenizer cardTokenizer;
    private final TransactionManagerFacade transactionManagerFacade;

    @Override
    public WalletResponse getWallet(String username) {
        XmlAppUser user = userService.getByUsername(username);
        Wallet wallet = getWalletByUserId(user.getId());
        return new WalletResponse(wallet.getId(), wallet.getBalance().setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    public TopUpResultResponse topUp(String username, TopUpRequest request) {
        XmlAppUser user = userService.getByUsername(username);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);

        BankCard card = bankCardRepository.findByToken(request.cardToken())
                .filter(c -> c.getUserId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Card not found"));

        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessException("Card is not active");
        }

        String correlationId = UUID.randomUUID().toString();

        transactionManagerFacade.execute(
                TransactionOptions.defaults("topup-prepare-record"),
                () -> bankOperationRepository.save(BankOperation.builder()
                        .correlationId(correlationId)
                        .type(BankOperationType.WALLET_TOPUP)
                        .status(BankOperationStatus.PREPARING)
                        .userId(user.getId())
                        .cardToken(card.getToken())
                        .maskedCard(card.getMaskedCardNumber())
                        .amount(amount)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .attempts(0)
                        .build()),
                null,
                ex -> log.error("Failed to record bank op [{}]: {}", correlationId, ex.getMessage())
        );

        String cardNumber = cardTokenizer.decrypt(card.getEncryptedCardNumber());

        try {
            BankChargeResult prepared = bankClient.prepareCharge(correlationId, cardNumber, amount.doubleValue());
            log.info("Bank prepared corr={} status={}", correlationId, prepared.status());
        } catch (BankTimeoutException | BankUnavailableException e) {
            markFailed(correlationId, "Bank unavailable: " + e.getMessage());
            throw new BankUnavailableException("Bank is down, please retry later", e);
        } catch (BusinessException e) {
            markFailed(correlationId, e.getMessage());
            throw e;
        }

        Wallet wallet = transactionManagerFacade.execute(
                TransactionOptions.defaults("topup-commit-db"),
                () -> {
                    BankOperation op = bankOperationRepository.findByCorrelationId(correlationId)
                            .orElseThrow(() -> new BusinessException("Bank op disappeared"));
                    op.setStatus(BankOperationStatus.COMMITTING);
                    op.setUpdatedAt(Instant.now());
                    bankOperationRepository.save(op);

                    credit(
                            user.getId(),
                            amount,
                            correlationId,
                            "Top-up from card " + card.getMaskedCardNumber(),
                            TransactionType.WALLET_TOP_UP
                    );
                    return getWalletByUserId(user.getId());
                },
                w -> log.info("Top-up DB committed [{}]", correlationId),
                ex -> {
                    log.error("Top-up DB commit failed [{}], rolling back bank: {}", correlationId, ex.getMessage());
                    safelyRollbackBank(correlationId);
                    markFailed(correlationId, "DB commit failed: " + ex.getMessage());
                }
        );

        try {
            bankClient.commitCharge(correlationId);
            markCommitted(correlationId);
        } catch (BankTimeoutException | BankUnavailableException e) {
            markPendingFinalize(correlationId, "COMMIT deferred: " + e.getMessage());
            log.warn("Commit deferred for {}, will be retried by reconciler", correlationId);
        } catch (BusinessException e) {
            log.error("Commit rejected by bank for {}: {}", correlationId, e.getMessage());
            markPendingFinalize(correlationId, "COMMIT rejected: " + e.getMessage());
        }

        return new TopUpResultResponse(
                "SUCCESS",
                correlationId,
                wallet.getId(),
                wallet.getBalance().setScale(2, RoundingMode.HALF_UP),
                null
        );
    }

    private void safelyRollbackBank(String correlationId) {
        try {
            bankClient.rollbackCharge(correlationId);
        } catch (Exception e) {
            log.error("Bank rollback failed for {}: {}", correlationId, e.getMessage());
        }
    }

    private void markPendingReconcile(String correlationId, String error) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("topup-mark-pending"),
                () -> {
                    BankOperation op = bankOperationRepository.findByCorrelationId(correlationId).orElseThrow();
                    op.setStatus(BankOperationStatus.PENDING_RECONCILE);
                    op.setUpdatedAt(Instant.now());
                    op.setLastError(error);
                    return bankOperationRepository.save(op);
                },
                null, null
        );
    }

    private void markPendingFinalize(String correlationId, String error) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("topup-mark-finalize"),
                () -> {
                    BankOperation op = bankOperationRepository.findByCorrelationId(correlationId).orElseThrow();
                    op.setStatus(BankOperationStatus.PENDING_FINALIZE);
                    op.setUpdatedAt(Instant.now());
                    op.setLastError(error);
                    return bankOperationRepository.save(op);
                },
                null, null
        );
    }

    private void markFailed(String correlationId, String error) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("topup-mark-failed"),
                () -> {
                    BankOperation op = bankOperationRepository.findByCorrelationId(correlationId).orElseThrow();
                    op.setStatus(BankOperationStatus.FAILED);
                    op.setUpdatedAt(Instant.now());
                    op.setLastError(error);
                    return bankOperationRepository.save(op);
                },
                null, null
        );
    }

    private void markCommitted(String correlationId) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("topup-mark-committed"),
                () -> {
                    BankOperation op = bankOperationRepository.findByCorrelationId(correlationId).orElseThrow();
                    op.setStatus(BankOperationStatus.COMMITTED);
                    op.setUpdatedAt(Instant.now());
                    return bankOperationRepository.save(op);
                },
                null, null
        );
    }

    @Override
    public List<TransactionResponse> getTransactions(String username) {
        XmlAppUser user = userService.getByUsername(username);
        Wallet wallet = getWalletByUserId(user.getId());
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(t -> new TransactionResponse(
                        t.getId(),
                        t.getType(),
                        t.getAmount().setScale(2, RoundingMode.HALF_UP),
                        t.getDescription(),
                        t.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public void debit(Long userId, BigDecimal amount, String operationId, String description, TransactionType type) {
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        Wallet wallet = walletRepository.findByOwnerIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));

        BigDecimal heldSum = walletHoldRepository.sumActiveByWalletId(wallet.getId());
        BigDecimal available = wallet.getBalance().subtract(heldSum);

        if (available.compareTo(amount) < 0) {
            throw new BusinessException("Insufficient available funds");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .wallet(wallet)
                .operationId(operationId)
                .type(type)
                .amount(amount.negate())
                .description(description)
                .createdAt(Instant.now())
                .build());
    }

    @Override
    public void credit(Long userId, BigDecimal amount, String operationId, String description, TransactionType type) {
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        Wallet wallet = walletRepository.findByOwnerIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .wallet(wallet)
                .operationId(operationId)
                .type(type)
                .amount(amount)
                .description(description)
                .createdAt(Instant.now())
                .build());
    }

    @Override
    public String placeHold(Long userId, BigDecimal amount, String externalRef, String reason) {
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        Optional<WalletHold> existing = walletHoldRepository.findByExternalRef(externalRef);
        if (existing.isPresent()) {
            return existing.get().getExternalRef();
        }

        Wallet wallet = walletRepository.findByOwnerIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));

        BigDecimal heldSum = walletHoldRepository.sumActiveByWalletId(wallet.getId());
        BigDecimal available = wallet.getBalance().subtract(heldSum);

        if (available.compareTo(amount) < 0) {
            throw new BusinessException("Insufficient available funds");
        }

        walletHoldRepository.save(WalletHold.builder()
                .wallet(wallet)
                .amount(amount)
                .status(HoldStatus.ACTIVE)
                .externalRef(externalRef)
                .reason(reason)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(HOLD_TTL))
                .build());

        return externalRef;
    }

    @Override
    public void captureHold(String externalRef, TransactionType type) {
        WalletHold hold = walletHoldRepository.findByExternalRef(externalRef)
                .orElseThrow(() -> new NotFoundException("Hold not found"));

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new BusinessException("Hold not active: " + hold.getStatus());
        }

        Wallet wallet = walletRepository.findByOwnerIdForUpdate(hold.getWallet().getUserId())
                .orElseThrow(() -> new BusinessException("Wallet not found"));

        if (wallet.getBalance().compareTo(hold.getAmount()) < 0) {
            throw new BusinessException("Inconsistent state: balance below hold amount");
        }

        wallet.setBalance(wallet.getBalance().subtract(hold.getAmount()));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .wallet(wallet)
                .operationId(externalRef)
                .type(type)
                .amount(hold.getAmount().negate())
                .description(hold.getReason())
                .createdAt(Instant.now())
                .build());

        hold.setStatus(HoldStatus.CAPTURED);
        hold.setResolvedAt(Instant.now());
        walletHoldRepository.save(hold);
    }

    @Override
    public void releaseHold(String externalRef) {
        WalletHold hold = walletHoldRepository.findByExternalRef(externalRef)
                .orElseThrow(() -> new NotFoundException("Hold not found"));

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            return;
        }

        hold.setStatus(HoldStatus.RELEASED);
        hold.setResolvedAt(Instant.now());
        walletHoldRepository.save(hold);
    }

    @Override
    public BigDecimal getAvailableBalance(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));
        BigDecimal held = walletHoldRepository.sumActiveByWalletId(wallet.getId());
        return wallet.getBalance().subtract(held).setScale(2, RoundingMode.HALF_UP);
    }

    private Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(Wallet.builder().userId(userId).build()));
    }
}