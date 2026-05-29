package com.example.labpay.controller;

import com.example.labpay.camunda.BpmProcessFacade;
import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.response.ListResponse;
import com.example.labpay.dto.response.PaymentOrderResponse;
import com.example.labpay.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Оплата товаров и услуг")
public class PaymentController {

    private final PaymentService paymentService;
    private final BpmProcessFacade bpmProcessFacade;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public PaymentOrderResponse create(Authentication auth, @Valid @RequestBody CreatePaymentRequest request) {
        return bpmProcessFacade.startPaymentCreate(auth.getName(), request);
    }

    @PostMapping("/process")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public PaymentOrderResponse process(Authentication auth, @Valid @RequestBody ProcessPaymentRequest request) {
        return bpmProcessFacade.startPaymentProcess(auth.getName(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public PaymentOrderResponse get(Authentication auth, @PathVariable Long id) {
        return paymentService.getOrder(auth.getName(), id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ListResponse<PaymentOrderResponse> list(Authentication auth) {
        return new ListResponse<>(paymentService.getUserOrders(auth.getName()));
    }
}
