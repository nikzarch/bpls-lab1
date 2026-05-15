package com.example.labpay.mq.events;

import java.math.BigDecimal;
import java.time.Instant;

public record BitrixDealSyncEvent(
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