package com.example.labpay.mq.events;

import java.math.BigDecimal;

public record WebhookEvent(
        String externalOrderId,
        String callbackUrl,
        String status,
        BigDecimal amount,
        int attempt
) {}