package com.example.labpay.controller;

import com.example.labpay.camunda.BpmProcessFacade;
import com.example.labpay.dto.request.TransferRequest;
import com.example.labpay.dto.response.ListResponse;
import com.example.labpay.dto.response.TransferResponse;
import com.example.labpay.service.TransferService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "P2P-переводы")
public class TransferController {

    private final TransferService transferService;
    private final BpmProcessFacade bpmProcessFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public TransferResponse create(Authentication auth, @Valid @RequestBody TransferRequest request) {
        return bpmProcessFacade.startTransfer(auth.getName(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public TransferResponse get(@PathVariable Long id) {
        return transferService.getTransfer(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ListResponse<TransferResponse> list(Authentication auth) {
        return new ListResponse<>(transferService.getUserTransfers(auth.getName()));
    }
}
