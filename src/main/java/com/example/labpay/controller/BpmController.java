package com.example.labpay.controller;

import com.example.labpay.camunda.BpmProcessFacade;
import com.example.labpay.camunda.ProcessStartAuthorizer;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/bpm")
public class BpmController {

    private final BpmProcessFacade bpmProcessFacade;
    private final ProcessStartAuthorizer processStartAuthorizer;

    public BpmController(BpmProcessFacade bpmProcessFacade, ProcessStartAuthorizer processStartAuthorizer) {
        this.bpmProcessFacade = bpmProcessFacade;
        this.processStartAuthorizer = processStartAuthorizer;
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

    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Set<String>> availableProcesses(Authentication auth) {
        Set<String> userGroups = new LinkedHashSet<>();
        for (var a : auth.getAuthorities()) {
            String r = a.getAuthority();
            userGroups.add(r.startsWith("ROLE_") ? r.substring(5) : r);
        }

        List<String> allKeys = List.of(
                "card-binding-process", "payment-create-process", "payment-process",
                "transfer-process", "wallet-top-up-process",
                "maintenance-bank-reconciliation", "maintenance-hold-expiration",
                "maintenance-card-session-cleanup", "maintenance-stuck-transfer"
        );

        Set<String> available = new LinkedHashSet<>();
        for (String key : allKeys) {
            if (processStartAuthorizer.canStart(userGroups, key)) {
                available.add(key);
            }
        }
        return Map.of("processes", available);
    }
}