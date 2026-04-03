package com.example.labpay.transaction;

import org.springframework.transaction.TransactionDefinition;

public record TransactionOptions(
        String name,
        int propagationBehavior,
        int timeout
) {
    public static TransactionOptions defaults(String name) {
        return new TransactionOptions(
                name,
                TransactionDefinition.PROPAGATION_REQUIRED,
                30
        );
    }
}