package com.example.labpay.service;

import com.example.labpay.exception.BankQueueUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

@Slf4j
@Component
public class BankQueueAvailabilityChecker {

    private final String jmsUrl;
    private final int timeoutMs;

    public BankQueueAvailabilityChecker(
            @Value("${app.jms.url:${APP_JMS_URL:amqp://localhost:5672}}") String jmsUrl,
            @Value("${app.jms.connect-timeout-ms:1000}") int timeoutMs
    ) {
        this.jmsUrl = jmsUrl;
        this.timeoutMs = timeoutMs;
    }

    public void assertQueueReachable() {
        URI uri = URI.create(jmsUrl);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5672;

        if (host == null || host.isBlank()) {
            throw new BankQueueUnavailableException("Bank queue is unavailable: invalid AMQP URL");
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
        } catch (Exception e) {
            log.warn("Bank queue is unreachable at {}:{}: {}", host, port, e.getMessage());
            throw new BankQueueUnavailableException("Bank queue is unavailable, please retry later", e);
        }
    }
}