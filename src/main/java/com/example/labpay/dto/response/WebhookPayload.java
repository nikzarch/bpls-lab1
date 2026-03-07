package com.example.labpay.dto.response;

import java.math.BigDecimal;

public record WebhookPayload(String externalOrderId, String status, BigDecimal amount, String signature) {}