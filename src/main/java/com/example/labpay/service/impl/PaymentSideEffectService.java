package com.example.labpay.service.impl;

import com.example.labpay.mq.EventPublisher;
import com.example.labpay.mq.events.BitrixDealSyncEvent;
import com.example.labpay.mq.events.WebhookEvent;
import com.example.labpay.service.BitrixCrmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSideEffectService {

    private final BitrixCrmService bitrixCrmService;
    private final EventPublisher eventPublisher;

    @Async("paymentSideEffectExecutor")
    public void afterPaidOrder(BitrixDealSyncEvent bitrixEvent, WebhookEvent webhookEvent) {
        if (bitrixEvent != null) {
            try {
                bitrixCrmService.syncPaidOrder(bitrixEvent);
            } catch (Exception e) {
                log.warn("Bitrix sync failed for order {}: {}",
                        bitrixEvent.externalOrderId(), e.getMessage(), e);
            }
        }

        if (webhookEvent != null) {
            try {
                eventPublisher.publishWebhook(webhookEvent);
            } catch (Exception e) {
                log.warn("Webhook publish failed for order {}: {}",
                        webhookEvent.externalOrderId(), e.getMessage(), e);
            }
        }
    }
}