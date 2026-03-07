package com.example.labpay.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @NotNull @Positive Long widgetId,
        @NotNull @Positive Long productId
) {}