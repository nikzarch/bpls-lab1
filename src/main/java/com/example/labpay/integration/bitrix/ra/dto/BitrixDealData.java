package com.example.labpay.integration.bitrix.ra.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BitrixDealData(
        Long orderId,
        String externalOrderId,
        String buyerUsername,
        Long widgetId,
        Long merchantId,
        String productTitle,
        BigDecimal amount,
        String status,
        Instant paidAt
) {
}