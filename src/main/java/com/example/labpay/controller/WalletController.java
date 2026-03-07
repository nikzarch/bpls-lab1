package com.example.labpay.controller;

import com.example.labpay.dto.request.TopUpRequest;
import com.example.labpay.dto.response.ListResponse;
import com.example.labpay.dto.response.TransactionResponse;
import com.example.labpay.dto.response.WalletResponse;
import com.example.labpay.service.WalletService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Кошелёк: баланс, пополнение, история")
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletResponse getWallet(Authentication auth) {
        return walletService.getWallet(auth.getName());
    }

    @PostMapping("/top-up")
    public WalletResponse topUp(Authentication auth, @Valid @RequestBody TopUpRequest request) {
        return walletService.topUp(auth.getName(), request);
    }

    @GetMapping("/transactions")
    public ListResponse<TransactionResponse> transactions(Authentication auth) {
        return new ListResponse<>(walletService.getTransactions(auth.getName()));
    }
}