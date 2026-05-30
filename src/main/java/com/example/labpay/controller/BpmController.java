package com.example.labpay.controller;

import com.example.labpay.camunda.BpmProcessFacade;
import com.example.labpay.dto.request.BindCardRequest;
import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.request.TopUpRequest;
import com.example.labpay.dto.request.TransferRequest;
import com.example.labpay.dto.response.PaymentOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bpm")
public class BpmController {

    private final BpmProcessFacade bpmProcessFacade;

    public BpmController(BpmProcessFacade bpmProcessFacade) {
        this.bpmProcessFacade = bpmProcessFacade;
    }

    @PostMapping("/cards/bind")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BpmProcessFacade.ProcessLaunchResult startCardBinding(
            Authentication auth,
            @Valid @RequestBody BindCardRequest request
    ) {
        return bpmProcessFacade.start("card-binding-process", Map.of(
                "username", auth.getName(),
                "cardNumber", request.cardNumber(),
                "holderName", request.holderName(),
                "expiryDate", request.expiryDate(),
                "cvv", request.cvv()
        ));
    }

    @PostMapping("/payments/create")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BpmProcessFacade.ProcessLaunchResult startPaymentCreate(
            Authentication auth,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        return bpmProcessFacade.start("payment-create-process", Map.of(
                "username", auth.getName(),
                "widgetId", request.widgetId(),
                "productId", request.productId()
        ));
    }

    @PostMapping("/payments/process")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentOrderResponse startPaymentProcess(
            Authentication auth,
            @Valid @RequestBody ProcessPaymentRequest request
    ) {
        return bpmProcessFacade.startPaymentProcess(auth.getName(), request);
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BpmProcessFacade.ProcessLaunchResult startTransfer(
            Authentication auth,
            @Valid @RequestBody TransferRequest request
    ) {
        return bpmProcessFacade.start("transfer-process", Map.of(
                "username", auth.getName(),
                "recipientId", request.recipientId(),
                "amount", request.amount().toPlainString(),
                "source", request.source().name(),
                "type", request.type().name(),
                "cardToken", request.cardToken() == null ? "" : request.cardToken(),
                "idempotencyKey", request.idempotencyKey() == null ? "" : request.idempotencyKey()
        ));
    }

    @PostMapping("/wallet/top-up")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BpmProcessFacade.ProcessLaunchResult startTopUp(
            Authentication auth,
            @Valid @RequestBody TopUpRequest request
    ) {
        return bpmProcessFacade.start("wallet-top-up-process", Map.of(
                "username", auth.getName(),
                "cardToken", request.cardToken(),
                "amount", request.amount().toPlainString()
        ));
    }

    @PostMapping("/maintenance/{jobName}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BpmProcessFacade.ProcessLaunchResult runMaintenance(@PathVariable String jobName) {
        return switch (jobName) {
            case "bank-reconciliation" -> bpmProcessFacade.start("maintenance-bank-reconciliation", Map.of("trigger", "manual"));
            case "hold-expiration" -> bpmProcessFacade.start("maintenance-hold-expiration", Map.of("trigger", "manual"));
            case "card-session-cleanup" -> bpmProcessFacade.start("maintenance-card-session-cleanup", Map.of("trigger", "manual"));
            case "stuck-transfer" -> bpmProcessFacade.start("maintenance-stuck-transfer", Map.of("trigger", "manual"));
            default -> throw new IllegalArgumentException("Unknown maintenance job: " + jobName);
        };
    }
}