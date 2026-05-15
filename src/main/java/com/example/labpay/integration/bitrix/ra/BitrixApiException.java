package com.example.labpay.integration.bitrix.ra;

public class BitrixApiException extends RuntimeException {
    public BitrixApiException(String message) {
        super(message);
    }

    public BitrixApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
