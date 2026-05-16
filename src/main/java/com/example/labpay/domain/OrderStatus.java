package com.example.labpay.domain;

public enum OrderStatus {
    CREATED,
    CHARGING,
    PAID,
    PENDING_RECONCILE,
    CANCELLED,
    FAILED
}