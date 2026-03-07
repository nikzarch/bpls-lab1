package com.example.labpay.service;

import com.example.labpay.dto.request.TopUpRequest;
import com.example.labpay.dto.response.TransactionResponse;
import com.example.labpay.dto.response.WalletResponse;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {
    WalletResponse getWallet(String username);
    WalletResponse topUp(String username, TopUpRequest request);
    List<TransactionResponse> getTransactions(String username);
    void debit(Long userId, BigDecimal amount, String operationId, String description, com.example.labpay.domain.wallet.TransactionType type);
    void credit(Long userId, BigDecimal amount, String operationId, String description, com.example.labpay.domain.wallet.TransactionType type);
}