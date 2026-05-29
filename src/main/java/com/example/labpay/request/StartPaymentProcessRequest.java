package com.example.labpay.request;

import com.example.labpay.dto.request.ProcessPaymentRequest.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StartPaymentProcessRequest(
        @NotNull @Positive Long widgetId,
        @NotNull @Positive Long productId,
        @NotNull PaymentMethod method,
        String cardToken
) {}
