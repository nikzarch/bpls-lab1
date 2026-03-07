package com.example.labpay.controller;

import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.response.PaymentOrderResponse;
import com.example.labpay.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Оплата товаров и услуг")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public PaymentOrderResponse create(Authentication auth, @RequestBody CreatePaymentRequest request) {
        return paymentService.createOrder(auth.getName(), request);
    }

    @PostMapping("/process")
    public PaymentOrderResponse process(Authentication auth, @RequestBody ProcessPaymentRequest request) {
        return paymentService.processPayment(auth.getName(), request);
    }

    @GetMapping("/{id}")
    public PaymentOrderResponse get(@PathVariable Long id) {
        return paymentService.getOrder(id);
    }

    @GetMapping
    public List<PaymentOrderResponse> list(Authentication auth) {
        return paymentService.getUserOrders(auth.getName());
    }
}