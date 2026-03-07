package com.example.labpay.service;

import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.response.PaymentOrderResponse;

import java.util.List;

public interface PaymentService {
    PaymentOrderResponse createOrder(String username, CreatePaymentRequest request);
    PaymentOrderResponse processPayment(String username, ProcessPaymentRequest request);
    PaymentOrderResponse getOrder(Long orderId);
    List<PaymentOrderResponse> getUserOrders(String username);
}