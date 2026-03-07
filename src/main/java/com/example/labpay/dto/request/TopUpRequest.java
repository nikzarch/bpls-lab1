package com.example.labpay.dto.request;

import java.math.BigDecimal;

public record TopUpRequest(String cardToken, BigDecimal amount) {}