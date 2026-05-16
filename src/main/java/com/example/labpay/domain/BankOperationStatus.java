package com.example.labpay.domain;

public enum BankOperationStatus {
    PREPARING,
    PREPARED,
    COMMITTING,
    COMMITTED,
    ROLLING_BACK,
    ROLLED_BACK,
    PENDING_RECONCILE,
    PENDING_FINALIZE,
    FAILED
}