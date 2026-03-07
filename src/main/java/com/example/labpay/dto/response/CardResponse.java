package com.example.labpay.dto.response;

import com.example.labpay.domain.card.CardStatus;

public record CardResponse(Long id, String maskedCardNumber, String holderName, CardStatus status, String token) {}