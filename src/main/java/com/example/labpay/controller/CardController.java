package com.example.labpay.controller;

import com.example.labpay.dto.request.BindCardRequest;
import com.example.labpay.dto.request.Confirm3dsRequest;
import com.example.labpay.dto.response.BindCardResultResponse;
import com.example.labpay.dto.response.CardResponse;
import com.example.labpay.service.CardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Привязка и управление картами")
public class CardController {

    private final CardService cardService;

    @PostMapping("/bind")
    public BindCardResultResponse bind(Authentication auth, @RequestBody BindCardRequest request) {
        return cardService.bindCard(auth.getName(), request);
    }

    @PostMapping("/confirm-3ds")
    public CardResponse confirm3ds(Authentication auth, @RequestBody Confirm3dsRequest request) {
        return cardService.confirm3ds(auth.getName(), request);
    }

    @GetMapping
    public List<CardResponse> list(Authentication auth) {
        return cardService.getUserCards(auth.getName());
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication auth, @PathVariable Long id) {
        cardService.deleteCard(auth.getName(), id);
    }
}