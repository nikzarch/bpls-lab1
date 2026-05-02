package com.example.labpay.service.impl;

import com.example.labpay.domain.transfer.Transfer;
import com.example.labpay.domain.transfer.TransferStatus;
import com.example.labpay.domain.wallet.TransactionType;
import com.example.labpay.dto.request.TransferRequest;
import com.example.labpay.dto.response.TransferResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.mq.EventPublisher;
import com.example.labpay.mq.events.NotificationEvent;
import com.example.labpay.repository.AppUserRepository;
import com.example.labpay.repository.TransferRepository;
import com.example.labpay.service.TransferService;
import com.example.labpay.service.UserService;
import com.example.labpay.service.WalletService;
import com.example.labpay.transaction.TransactionManagerFacade;
import com.example.labpay.transaction.TransactionOptions;
import com.example.labpay.xml.XmlAppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private static final BigDecimal MAX_SINGLE_TRANSFER = new BigDecimal("150000");

    private final TransferRepository transferRepository;
    private final AppUserRepository appUserRepository;
    private final UserService userService;
    private final WalletService walletService;
    private final TransactionManagerFacade transactionManagerFacade;
    private final EventPublisher eventPublisher;

    @Override
    public TransferResponse createTransfer(String username, TransferRequest request) {
        Transfer transfer = transactionManagerFacade.execute(
                TransactionOptions.defaults("create-transfer-transaction"),
                () -> {
                    XmlAppUser sender = userService.getByUsername(username);
                    XmlAppUser recipient = appUserRepository.findById(request.recipientId())
                            .orElseThrow(() -> new NotFoundException("Recipient not found"));

                    if (sender.getId().equals(recipient.getId())) {
                        throw new BusinessException("Cannot transfer to yourself");
                    }

                    BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
                    if (amount.compareTo(MAX_SINGLE_TRANSFER) > 0) {
                        throw new BusinessException("Transfer exceeds wallet limit");
                    }

                    String idempotencyKey = request.idempotencyKey() != null && !request.idempotencyKey().isBlank()
                            ? request.idempotencyKey()
                            : UUID.randomUUID().toString();

                    var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
                    if (existing.isPresent()) {
                        return existing.get();
                    }

                    Transfer created = transferRepository.save(Transfer.builder()
                            .senderId(sender.getId())
                            .recipientId(recipient.getId())
                            .amount(amount)
                            .type(request.type())
                            .status(TransferStatus.PENDING)
                            .idempotencyKey(idempotencyKey)
                            .createdAt(Instant.now())
                            .build());

                    String holdRef = "transfer-" + idempotencyKey;
                    walletService.placeHold(
                            sender.getId(),
                            amount,
                            holdRef,
                            "Transfer to user #" + recipient.getId()
                    );

                    try {
                        walletService.captureHold(holdRef, TransactionType.WALLET_TRANSFER_OUT);
                        walletService.credit(
                                recipient.getId(),
                                amount,
                                holdRef,
                                "Transfer from user #" + sender.getId(),
                                TransactionType.WALLET_TRANSFER_IN
                        );
                    } catch (RuntimeException e) {
                        walletService.releaseHold(holdRef);
                        throw e;
                    }

                    created.setStatus(TransferStatus.SUCCESS);
                    created.setCompletedAt(Instant.now());
                    Transfer saved = transferRepository.save(created);

                    eventPublisher.publishNotification(new NotificationEvent(
                            "TRANSFER_RECEIVED",
                            recipient.getId(),
                            amount,
                            "Получен перевод от пользователя #" + sender.getId()
                    ));

                    return saved;
                },
                committedTransfer -> log.info("Transfer {} committed", committedTransfer.getId()),
                ex -> log.error("Transfer rolled back for user {}: {}", username, ex.getMessage())
        );

        return toResponse(transfer);
    }

    @Override
    public TransferResponse getTransfer(Long id) {
        Transfer t = transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        return toResponse(t);
    }

    @Override
    public List<TransferResponse> getUserTransfers(String username) {
        XmlAppUser user = userService.getByUsername(username);
        return transferRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(user.getId(), user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private TransferResponse toResponse(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getSenderId(),
                t.getRecipientId(),
                t.getAmount().setScale(2, RoundingMode.HALF_UP),
                t.getType(),
                t.getStatus(),
                t.getCreatedAt()
        );
    }
}