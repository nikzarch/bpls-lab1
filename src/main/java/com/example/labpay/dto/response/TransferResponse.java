package com.example.labpay.dto.response;

import com.example.labpay.domain.transfer.TransferStatus;
import com.example.labpay.domain.transfer.TransferType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(Long id, Long senderId, Long recipientId, BigDecimal amount, TransferType type, TransferStatus status, Instant createdAt) {}