package com.example.labpay.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProcessPaymentRequest(
        @NotNull @Positive Long orderId,
        @NotNull PaymentMethod method,
        String cardToken
) {
    public enum PaymentMethod { CARD, WALLET }
}