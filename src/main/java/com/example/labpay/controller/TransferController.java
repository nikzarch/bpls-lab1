package com.example.labpay.controller;

import com.example.labpay.dto.request.TransferRequest;
import com.example.labpay.dto.response.TransferResponse;
import com.example.labpay.service.TransferService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "P2P-переводы")
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public TransferResponse create(Authentication auth, @RequestBody TransferRequest request) {
        return transferService.createTransfer(auth.getName(), request);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable Long id) {
        return transferService.getTransfer(id);
    }

    @GetMapping
    public List<TransferResponse> list(Authentication auth) {
        return transferService.getUserTransfers(auth.getName());
    }
}