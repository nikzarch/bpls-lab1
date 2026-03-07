package com.example.labpay.dto.request;

public record ProcessPaymentRequest(Long orderId, PaymentMethod method, String cardToken) {
    public enum PaymentMethod { CARD, WALLET }
}