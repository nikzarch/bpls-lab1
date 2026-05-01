package com.example.labpay.service.impl;

import com.example.labpay.exception.BusinessException;
import com.example.labpay.mq.BankCommandMessage;
import com.example.labpay.mq.BankReplyMessage;
import com.example.labpay.service.BankClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankClientImpl implements BankClient {

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String REQUEST_QUEUE = "bank.requests";
    private static final String RESPONSE_QUEUE = "bank.responses";

    private BankReplyMessage call(String op, Object payloadObj) {
        try {
            String correlationId = UUID.randomUUID().toString();

            String payload = mapper.writeValueAsString(payloadObj);

            BankCommandMessage cmd =
                    new BankCommandMessage(correlationId, op, payload, RESPONSE_QUEUE);

            jmsTemplate.convertAndSend(REQUEST_QUEUE, mapper.writeValueAsString(cmd));

            String raw = (String) jmsTemplate.receiveAndConvert(RESPONSE_QUEUE);

            BankReplyMessage reply =
                    mapper.readValue(raw, BankReplyMessage.class);

            if (!reply.correlationId().equals(correlationId)) {
                throw new BusinessException("Invalid bank reply");
            }

            if (!reply.ok()) {
                throw new BusinessException(reply.error());
            }

            return reply;

        } catch (Exception e) {
            throw new BusinessException("Bank unavailable");
        }
    }

    @Override
    public String initiateBind(String cardNumber, String cvv, String expiry) {
        return call("INIT_BIND",
                Map.of(
                        "cardNumber", cardNumber,
                        "cvv", cvv,
                        "expiry", expiry
                )).payload();
    }

    @Override
    public void confirm3ds(String sessionId, String code) {
        call("CONFIRM_3DS",
                Map.of(
                        "sessionId", sessionId,
                        "code", code
                ));
    }

    @Override
    public String initiateCharge(String cardNumber, double amount) {
        return call("INIT_CHARGE",
                Map.of(
                        "cardNumber", cardNumber,
                        "amount", amount
                )).payload();
    }

    @Override
    public void completeCharge(String sessionId, double amount) {
        call("COMPLETE_CHARGE",
                Map.of(
                        "sessionId", sessionId,
                        "amount", amount
                ));
    }

    @Override
    public void directCharge(String cardNumber, double amount) {
        call("DIRECT_CHARGE",
                Map.of(
                        "cardNumber", cardNumber,
                        "amount", amount
                ));
    }
}