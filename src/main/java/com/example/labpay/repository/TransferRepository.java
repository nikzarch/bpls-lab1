package com.example.labpay.repository;

import com.example.labpay.domain.transfer.Transfer;
import com.example.labpay.domain.transfer.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    List<Transfer> findBySenderIdOrRecipientIdOrderByCreatedAtDesc(Long senderId, Long recipientId);
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
    List<Transfer> findByStatusAndCreatedAtBefore(TransferStatus status, Instant before);
}