package com.example.labpay.service.impl;

import com.example.bankra.BankConnection;
import com.example.bankra.BankConnectionFactory;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.service.BankClient;
import jakarta.resource.ResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankClientImpl implements BankClient {

    private final BankConnectionFactory bankConnectionFactory;

    @Override
    public String initiateBind(String cardNumber, String cvv, String expiry) {
        try (BankConnection c = open()) {
            return c.initiateBind(cardNumber, cvv, expiry);
        }
    }

    @Override
    public void confirm3ds(String sessionId, String code) {
        try (BankConnection c = open()) {
            c.confirm3ds(sessionId, code);
        }
    }

    @Override
    public String initiateCharge(String cardNumber, double amount) {
        try (BankConnection c = open()) {
            return c.initiateCharge(cardNumber, amount);
        }
    }

    @Override
    public void completeCharge(String sessionId, double amount) {
        try (BankConnection c = open()) {
            c.completeCharge(sessionId, amount);
        }
    }

    @Override
    public void directCharge(String cardNumber, double amount) {
        try (BankConnection c = open()) {
            c.directCharge(cardNumber, amount);
        }
    }

    private BankConnection open() {
        try {
            return bankConnectionFactory.getConnection();
        } catch (ResourceException e) {
            throw new BusinessException("Bank adapter unavailable: " + e.getMessage());
        }
    }
}