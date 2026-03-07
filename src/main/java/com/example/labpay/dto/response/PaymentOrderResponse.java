package com.example.labpay.dto.response;

import com.example.labpay.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentOrderResponse(Long id, String externalOrderId, OrderStatus status, BigDecimal amount, String productTitle, Instant createdAt, Instant paidAt) {}