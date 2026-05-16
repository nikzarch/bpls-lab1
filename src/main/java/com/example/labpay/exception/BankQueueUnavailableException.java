package com.example.labpay.exception;

public class BankQueueUnavailableException extends RuntimeException {

    public BankQueueUnavailableException(String message) {
        super(message);
    }

    public BankQueueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}