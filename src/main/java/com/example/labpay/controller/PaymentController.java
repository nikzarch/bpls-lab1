package com.example.labpay.controller;

import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.response.ListResponse;
import com.example.labpay.dto.response.PaymentOrderResponse;
import com.example.labpay.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Оплата товаров и услуг")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentOrderResponse create(Authentication auth, @Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createOrder(auth.getName(), request);
    }

    @PostMapping("/process")
    public PaymentOrderResponse process(Authentication auth, @Valid @RequestBody ProcessPaymentRequest request) {
        return paymentService.processPayment(auth.getName(), request);
    }

    @GetMapping("/{id}")
    public PaymentOrderResponse get(@PathVariable Long id) {
        return paymentService.getOrder(id);
    }

    @GetMapping
    public ListResponse<PaymentOrderResponse> list(Authentication auth) {
        return new ListResponse<>(paymentService.getUserOrders(auth.getName()));
    }
}