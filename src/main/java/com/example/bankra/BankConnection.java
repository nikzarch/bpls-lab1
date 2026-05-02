package com.example.bankra;

public interface BankConnection extends AutoCloseable {
    void validate(String cardNumber);
    String initiateBind(String cardNumber, String cvv, String expiry);
    void confirm3ds(String sessionId, String code);
    String initiateCharge(String cardNumber, double amount);
    void completeCharge(String sessionId, double amount);
    void directCharge(String cardNumber, double amount);

    @Override
    void close();
}