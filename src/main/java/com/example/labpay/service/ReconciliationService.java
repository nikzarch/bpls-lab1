package com.example.labpay.service;

import com.example.labpay.domain.BankOperation;

public interface ReconciliationService {
    void reconcile(BankOperation op);
}