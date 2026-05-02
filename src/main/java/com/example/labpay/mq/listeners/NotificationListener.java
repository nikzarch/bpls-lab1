package com.example.labpay.mq.listeners;

import com.example.labpay.mq.events.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"worker", "all"})
public class NotificationListener {

    @JmsListener(destination = "user.notifications", containerFactory = "jmsListenerContainerFactory")
    public void onNotification(NotificationEvent e) {
        log.info("[NOTIFY] user={} type={} msg={}", e.userId(), e.type(), e.message());
    }
}