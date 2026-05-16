package com.example.labpay.dto.response;

import java.math.BigDecimal;

public record TopUpResultResponse(
        String state,
        String correlationId,
        Long walletId,
        BigDecimal balance,
        String message
) {
}