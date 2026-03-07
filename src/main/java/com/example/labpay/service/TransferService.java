package com.example.labpay.service;

import com.example.labpay.dto.request.TransferRequest;
import com.example.labpay.dto.response.TransferResponse;

import java.util.List;

public interface TransferService {
    TransferResponse createTransfer(String username, TransferRequest request);
    TransferResponse getTransfer(Long id);
    List<TransferResponse> getUserTransfers(String username);
}