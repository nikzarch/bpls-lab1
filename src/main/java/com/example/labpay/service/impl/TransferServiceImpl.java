package com.example.labpay.service.impl;

import com.example.labpay.domain.transfer.Transfer;
import com.example.labpay.domain.transfer.TransferStatus;
import com.example.labpay.domain.user.AppUser;
import com.example.labpay.domain.wallet.TransactionType;
import com.example.labpay.dto.request.TransferRequest;
import com.example.labpay.dto.response.TransferResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.repository.AppUserRepository;
import com.example.labpay.repository.TransferRepository;
import com.example.labpay.service.TransferService;
import com.example.labpay.service.UserService;
import com.example.labpay.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Override
    @Transactional
    public TransferResponse createTransfer(String username, TransferRequest request) {
        AppUser sender = userService.getByUsername(username);
        AppUser recipient = appUserRepository.findById(request.recipientId())
                .orElseThrow(() -> new BusinessException("Recipient not found"));

        if (sender.getId().equals(recipient.getId())) {
            throw new BusinessException("Cannot transfer to yourself");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be positive");
        }
        if (request.amount().compareTo(MAX_SINGLE_TRANSFER) > 0) {
            throw new BusinessException("Transfer exceeds wallet limit");
        }

        String idempotencyKey = request.idempotencyKey() != null ? request.idempotencyKey() : UUID.randomUUID().toString();
        var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Transfer transfer = transferRepository.save(Transfer.builder()
                .sender(sender)
                .recipient(recipient)
                .amount(request.amount())
                .type(request.type())
                .status(TransferStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .build());

        try {
            String opId = UUID.randomUUID().toString();
            walletService.debit(sender.getId(), request.amount(), opId,
                    "Transfer to user #" + recipient.getId(), TransactionType.WALLET_TRANSFER_OUT);
            walletService.credit(recipient.getId(), request.amount(), opId,
                    "Transfer from user #" + sender.getId(), TransactionType.WALLET_TRANSFER_IN);

            transfer.setStatus(TransferStatus.SUCCESS);
            transfer.setCompletedAt(Instant.now());
        } catch (BusinessException e) {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setCompletedAt(Instant.now());
            transferRepository.save(transfer);
            throw e;
        }

        transferRepository.save(transfer);
        log.info("Transfer {} completed: {} -> {} amount={}", transfer.getId(), sender.getId(), recipient.getId(), request.amount());
        return toResponse(transfer);
    }

    @Override
    public TransferResponse getTransfer(Long id) {
        Transfer t = transferRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Transfer not found"));
        return toResponse(t);
    }

    @Override
    public List<TransferResponse> getUserTransfers(String username) {
        AppUser user = userService.getByUsername(username);
        return transferRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(user.getId(), user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private TransferResponse toResponse(Transfer t) {
        return new TransferResponse(t.getId(), t.getSender().getId(), t.getRecipient().getId(),
                t.getAmount(), t.getType(), t.getStatus(), t.getCreatedAt());
    }
}