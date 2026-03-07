package com.example.labpay.dto.response;

public record BindCardResultResponse(boolean requires3ds, String sessionId, String confirmationCode, CardResponse card) {}