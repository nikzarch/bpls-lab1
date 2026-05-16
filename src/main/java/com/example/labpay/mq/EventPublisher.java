package com.example.labpay.mq;

import com.example.labpay.mq.events.NotificationEvent;
import com.example.labpay.mq.events.WebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    public static final String QUEUE_WEBHOOKS = "payment.webhooks";
    public static final String QUEUE_NOTIFICATIONS = "user.notifications";

    private final JmsTemplate jmsTemplate;

    public void publishWebhook(WebhookEvent e) {
        safePublish(QUEUE_WEBHOOKS, e);
    }

    public void publishNotification(NotificationEvent e) {
        safePublish(QUEUE_NOTIFICATIONS, e);
    }

    private void safePublish(String queue, Object event) {
        try {
            jmsTemplate.convertAndSend(queue, event);
        } catch (RuntimeException ex) {
            log.warn("Failed to publish event to {}: {}", queue, ex.getMessage(), ex);
        }
    }
}