package com.example.labpay.mq;

public record BankCommandMessage(
        String correlationId,
        String operation,
        String payload,
        String replyQueue
) {}