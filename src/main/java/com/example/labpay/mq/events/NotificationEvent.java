package com.example.labpay.mq.events;

import java.math.BigDecimal;

public record NotificationEvent(
        String type,
        Long userId,
        BigDecimal amount,
        String message
) {}