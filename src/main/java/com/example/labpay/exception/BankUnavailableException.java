package com.example.labpay.exception;

public class BankUnavailableException extends RuntimeException {
    public BankUnavailableException(String message) { super(message); }
    public BankUnavailableException(String message, Throwable cause) { super(message, cause); }
}