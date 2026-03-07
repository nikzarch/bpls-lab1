package com.example.labpay.dto.request;

public record BindCardRequest(String cardNumber, String holderName, String expiryDate, String cvv) {}