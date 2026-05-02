package com.example.labpay.mq;

import com.example.labpay.mq.events.NotificationEvent;
import com.example.labpay.mq.events.WebhookEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventPublisher {

    public static final String QUEUE_WEBHOOKS = "/queues/payment.webhooks";
    public static final String QUEUE_NOTIFICATIONS = "/queues/user.notifications";
    private final JmsTemplate jmsTemplate;

    public void publishWebhook(WebhookEvent e) {
        jmsTemplate.convertAndSend(QUEUE_WEBHOOKS, e);
    }

    public void publishNotification(NotificationEvent e) {
        jmsTemplate.convertAndSend(QUEUE_NOTIFICATIONS, e);
    }
}