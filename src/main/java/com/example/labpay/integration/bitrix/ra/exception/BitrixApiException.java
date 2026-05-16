package com.example.labpay.integration.bitrix.ra.exception;

public class BitrixApiException extends RuntimeException {
    public BitrixApiException(String message) {
        super(message);
    }

    public BitrixApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
