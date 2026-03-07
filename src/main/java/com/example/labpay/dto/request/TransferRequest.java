package com.example.labpay.dto.request;

import com.example.labpay.domain.transfer.TransferType;
import java.math.BigDecimal;

public record TransferRequest(Long recipientId, BigDecimal amount, TransferSource source, TransferType type, String cardToken, String idempotencyKey) {
    public enum TransferSource { WALLET, CARD }
}