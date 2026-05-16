package com.example.labpay.exception;

public class BankTimeoutException extends RuntimeException {
    private final String correlationId;

    public BankTimeoutException(String correlationId, String message) {
        super(message);
        this.correlationId = correlationId;
    }

    public String getCorrelationId() { return correlationId; }
}