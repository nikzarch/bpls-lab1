package com.example.labpay.service;

public interface BankClient {
    String initiateBind(String cardNumber, String cvv, String expiry);
    void confirm3ds(String sessionId, String code);
    String initiateCharge(String cardNumber, double amount);
    void completeCharge(String sessionId, double amount);
    void directCharge(String cardNumber, double amount);
}