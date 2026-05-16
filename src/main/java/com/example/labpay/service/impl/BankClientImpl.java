package com.example.labpay.service.impl;

import com.example.labpay.exception.BankTimeoutException;
import com.example.labpay.exception.BankUnavailableException;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.mq.BankCommandMessage;
import com.example.labpay.mq.BankReplyMessage;
import com.example.labpay.service.BankClient;
import com.example.labpay.service.dto.BankChargeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Service;
import jakarta.jms.DeliveryMode;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class BankClientImpl implements BankClient {

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String requestQueue;
    private final String replyQueue;
    private final long receiveTimeoutMs;

    public BankClientImpl(
            JmsTemplate jmsTemplate,
            @Value("${app.bank.request-queue:bank.requests}") String requestQueue,
            @Value("${app.bank.reply-queue:bank.responses}") String replyQueue,
            @Value("${app.bank.receive-timeout-ms:5000}") long receiveTimeoutMs
    ) {
        this.jmsTemplate = jmsTemplate;
        this.requestQueue = requestQueue;
        this.replyQueue = replyQueue;
        this.receiveTimeoutMs = receiveTimeoutMs;
    }

    private BankReplyMessage call(String op, String correlationId, Object payloadObj) {
        try {
            String payload = mapper.writeValueAsString(payloadObj);
            BankCommandMessage cmd = new BankCommandMessage(correlationId, op, payload, replyQueue);
            String json = mapper.writeValueAsString(cmd);

            try {
                /*
                 * Important:
                 * If bank is down, RabbitMQ must not keep this request forever.
                 * Otherwise old PREPARE_CHARGE messages are processed when bank starts again.
                 */
                jmsTemplate.execute(session -> {
                    Queue destination = session.createQueue(requestQueue);

                    try (MessageProducer producer = session.createProducer(destination)) {
                        TextMessage m = session.createTextMessage(json);
                        m.setJMSCorrelationID(correlationId);

                        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                        producer.setTimeToLive(receiveTimeoutMs);
                        producer.send(m);
                    }

                    return null;
                }, true);
            } catch (JmsException ex) {
                log.warn("Bank send failed [{} corr={}]: {}", op, correlationId, ex.getMessage());
                throw new BankUnavailableException("Bank is down: request could not be sent", ex);
            }

            String selector = "JMSCorrelationID = '" + correlationId + "'";
            long previous = jmsTemplate.getReceiveTimeout();
            jmsTemplate.setReceiveTimeout(receiveTimeoutMs);

            Message reply;
            try {
                reply = jmsTemplate.receiveSelected(replyQueue, selector);
            } catch (JmsException ex) {
                log.warn("Bank receive failed [{} corr={}]: {}", op, correlationId, ex.getMessage());
                throw new BankUnavailableException("Bank is down: reply could not be received", ex);
            } finally {
                jmsTemplate.setReceiveTimeout(previous);
            }

            if (reply == null) {
                log.warn("Bank did not reply [{} corr={}] within {}ms", op, correlationId, receiveTimeoutMs);
                throw new BankUnavailableException("Bank is down: no response within " + receiveTimeoutMs + "ms");
            }

            String raw;
            if (reply instanceof TextMessage tm) {
                raw = tm.getText();
            } else {
                raw = reply.getBody(String.class);
            }

            BankReplyMessage parsed = mapper.readValue(raw, BankReplyMessage.class);

            if (parsed.correlationId() != null && !parsed.correlationId().equals(correlationId)) {
                log.warn(
                        "Bank correlation mismatch: expected={}, actual={}, op={}, raw={}",
                        correlationId,
                        parsed.correlationId(),
                        op,
                        raw
                );

                throw new BankUnavailableException("Bank returned mismatched correlation id");
            }

            return parsed;

        } catch (BankUnavailableException | BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Bank call unexpected failure [{} corr={}]", op, correlationId, e);
            throw new BankUnavailableException("Bank call failed: " + e.getMessage(), e);
        }
    }

    private BankChargeResult parseCharge(String payload, String fallbackCorr) {
        try {
            if (payload == null || payload.isBlank()) {
                return BankChargeResult.notFound(fallbackCorr);
            }
            var node = mapper.readTree(payload);
            if (node.has("status") && "NOT_FOUND".equals(node.path("status").asText())) {
                return BankChargeResult.notFound(fallbackCorr);
            }
            String corr = node.path("correlation_id").asText(fallbackCorr);
            String status = node.path("status").asText("UNKNOWN");
            BigDecimal amount = node.has("amount") ? new BigDecimal(node.path("amount").asText("0")) : null;
            String cardNumber = node.path("card_number").asText(null);
            boolean direct = node.path("direct").asBoolean(false);
            Instant expiresAt = node.hasNonNull("expires_at") && !node.path("expires_at").asText().isEmpty()
                    ? Instant.parse(node.path("expires_at").asText()) : null;
            Instant resolvedAt = node.hasNonNull("resolved_at") && !node.path("resolved_at").asText().isEmpty()
                    ? Instant.parse(node.path("resolved_at").asText()) : null;
            String error = node.path("error").asText(null);
            return new BankChargeResult(corr, status, amount, cardNumber, direct, expiresAt, resolvedAt, error);
        } catch (Exception e) {
            log.warn("Failed to parse charge payload: {}", e.getMessage());
            return BankChargeResult.notFound(fallbackCorr);
        }
    }

    @Override
    public String initiateBind(String cardNumber, String cvv, String expiry) {
        BankReplyMessage r = call("INIT_BIND", UUID.randomUUID().toString(),
                Map.of("cardNumber", cardNumber, "cvv", cvv, "expiry", expiry));
        if (!r.ok()) throw new BusinessException(r.error());
        return r.payload();
    }

    @Override
    public void confirm3ds(String sessionId, String code) {
        BankReplyMessage r = call("CONFIRM_3DS", UUID.randomUUID().toString(),
                Map.of("sessionId", sessionId, "code", code));
        if (!r.ok()) throw new BusinessException(r.error());
    }

    @Override
    public String initiateCharge(String cardNumber, double amount) {
        BankReplyMessage r = call("INIT_CHARGE", UUID.randomUUID().toString(),
                Map.of("cardNumber", cardNumber, "amount", amount));
        if (!r.ok()) throw new BusinessException(r.error());
        return r.payload();
    }

    @Override
    public void completeCharge(String sessionId, double amount) {
        BankReplyMessage r = call("COMPLETE_CHARGE", UUID.randomUUID().toString(),
                Map.of("sessionId", sessionId, "amount", amount));
        if (!r.ok()) throw new BusinessException(r.error());
    }

    @Override
    public BankChargeResult prepareCharge(String correlationId, String cardNumber, double amount) {
        BankReplyMessage r = call("PREPARE_CHARGE", correlationId,
                Map.of("correlationId", correlationId, "cardNumber", cardNumber, "amount", amount));
        if (!r.ok()) throw new BusinessException(r.error());
        return parseCharge(r.payload(), correlationId);
    }

    @Override
    public BankChargeResult commitCharge(String correlationId) {
        BankReplyMessage r = call("COMMIT_CHARGE_2PC", correlationId,
                Map.of("correlationId", correlationId));
        if (!r.ok()) throw new BusinessException(r.error());
        return parseCharge(r.payload(), correlationId);
    }

    @Override
    public BankChargeResult rollbackCharge(String correlationId) {
        BankReplyMessage r = call("ROLLBACK_CHARGE", correlationId,
                Map.of("correlationId", correlationId));
        if (!r.ok()) throw new BusinessException(r.error());
        return parseCharge(r.payload(), correlationId);
    }

    @Override
    public BankChargeResult directCharge(String correlationId, String cardNumber, double amount) {
        BankReplyMessage r = call("DIRECT_CHARGE", correlationId,
                Map.of("correlationId", correlationId, "cardNumber", cardNumber, "amount", amount));
        if (!r.ok()) throw new BusinessException(r.error());
        return parseCharge(r.payload(), correlationId);
    }

    @Override
    public BankChargeResult getChargeStatus(String correlationId) {
        BankReplyMessage r = call("GET_CHARGE_STATUS", correlationId,
                Map.of("correlationId", correlationId));
        if (!r.ok()) throw new BusinessException(r.error());
        return parseCharge(r.payload(), correlationId);
    }
}