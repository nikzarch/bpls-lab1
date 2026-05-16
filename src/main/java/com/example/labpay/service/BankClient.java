package com.example.labpay.service;

import com.example.labpay.service.dto.BankChargeResult;

public interface BankClient {
    String initiateBind(String cardNumber, String cvv, String expiry);
    void confirm3ds(String sessionId, String code);
    String initiateCharge(String cardNumber, double amount);
    void completeCharge(String sessionId, double amount);

    BankChargeResult prepareCharge(String correlationId, String cardNumber, double amount);
    BankChargeResult commitCharge(String correlationId);
    BankChargeResult rollbackCharge(String correlationId);
    BankChargeResult directCharge(String correlationId, String cardNumber, double amount);
    BankChargeResult getChargeStatus(String correlationId);
}