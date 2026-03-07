package com.example.labpay.dto.response;

import com.example.labpay.domain.wallet.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(Long id, TransactionType type, BigDecimal amount, String description, Instant createdAt) {}