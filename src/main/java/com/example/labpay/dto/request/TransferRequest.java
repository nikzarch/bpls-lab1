package com.example.labpay.dto.request;

import com.example.labpay.domain.transfer.TransferType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequest(

        @NotNull(message = "Recipient ID is required")
        @Positive(message = "Recipient ID must be a positive number")
        Long recipientId,

        @NotNull(message = "Transfer amount is required")
        @DecimalMin(value = "0.01", message = "Minimum transfer amount is 0.01")
        @DecimalMax(value = "1000000.00", message = "Maximum transfer amount is 1,000,000.00")
        @Digits(integer = 17, fraction = 2,
                message = "Amount must have at most 17 digits and 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Transfer source is required")
        TransferSource source,

        @NotNull(message = "Transfer type is required")
        TransferType type,

        String cardToken,
        String idempotencyKey
) {
    public enum TransferSource { WALLET, CARD }
}