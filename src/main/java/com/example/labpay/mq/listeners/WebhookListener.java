package com.example.labpay.mq.listeners;

import com.example.labpay.mq.events.WebhookEvent;
import com.example.labpay.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@Profile({"worker", "all"})
@RequiredArgsConstructor
public class WebhookListener {

    private final RestClient restClient = RestClient.create();

    @Value("${app.webhook.secret}")
    private String webhookSecret;

    @JmsListener(destination = "payment.webhooks", containerFactory = "jmsListenerContainerFactory")
    public void onWebhook(WebhookEvent e) {
        String data = e.externalOrderId() + ":" + e.amount().toPlainString() + ":" + e.status();
        String signature = HmacUtil.sign(data, webhookSecret);

        try {
            restClient.post()
                    .uri(e.callbackUrl())
                    .header("X-Signature", signature)
                    .body(e)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Webhook delivered: {}", e.externalOrderId());
        } catch (Exception ex) {
            log.warn("Webhook delivery failed for {}: {}", e.externalOrderId(), ex.getMessage());
            throw ex;
        }
    }
}