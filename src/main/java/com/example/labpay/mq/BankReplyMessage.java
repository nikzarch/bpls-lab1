package com.example.labpay.mq;

public record BankReplyMessage(
        String correlationId,
        boolean ok,
        String payload,
        String error
) {}