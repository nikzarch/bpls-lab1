package com.example.labpay.service.impl;

import com.example.labpay.exception.BusinessException;
import com.example.labpay.service.BankClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class BankClientImpl implements BankClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.bank.url:http://localhost:9090}")
    private String bankUrl;

    @Override
    public String initiateBind(String cardNumber) {
        JsonNode resp = post("/bind", "{\"card_number\":\"" + cardNumber + "\"}");
        if (!resp.get("ok").asBoolean()) {
            throw new BusinessException("Bank rejected card: " + resp.get("error").asText());
        }
        return resp.get("session_id").asText();
    }

    @Override
    public void confirm3ds(String sessionId, String code) {
        JsonNode resp = post("/confirm-3ds",
                "{\"session_id\":\"" + sessionId + "\",\"code\":\"" + code + "\"}");
        if (!resp.get("ok").asBoolean()) {
            throw new BusinessException("3DS failed: " + resp.get("error").asText());
        }
    }

    @Override
    public String initiateCharge(String cardNumber, double amount) {
        JsonNode resp = post("/charge",
                "{\"card_number\":\"" + cardNumber + "\",\"amount\":" + amount + "}");
        if (!resp.get("ok").asBoolean()) {
            throw new BusinessException("Charge failed: " + resp.get("error").asText());
        }
        return resp.get("session_id").asText();
    }

    @Override
    public void completeCharge(String sessionId, double amount) {
        JsonNode resp = post("/complete-charge",
                "{\"session_id\":\"" + sessionId + "\",\"amount\":" + amount + "}");
        if (!resp.get("ok").asBoolean()) {
            throw new BusinessException("Charge completion failed: " + resp.get("error").asText());
        }
    }

    private JsonNode post(String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(bankUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Bank service unavailable: " + e.getMessage());
        }
    }
}