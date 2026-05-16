package com.example.labpay.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BankChargeResult(
        String correlationId,
        String status,
        BigDecimal amount,
        String cardNumber,
        boolean direct,
        Instant expiresAt,
        Instant resolvedAt,
        String error
) {
    public static BankChargeResult notFound(String corrId) {
        return new BankChargeResult(corrId, "NOT_FOUND", null, null, false, null, null, null);
    }
}