package com.example.labpay.dto.request;

import com.example.labpay.domain.transfer.TransferType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull @Positive Long recipientId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull TransferSource source,
        @NotNull TransferType type,
        String cardToken,
        String idempotencyKey
) {
    public enum TransferSource { WALLET, CARD }
}