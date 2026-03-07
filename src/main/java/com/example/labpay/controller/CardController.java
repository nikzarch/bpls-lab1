package com.example.labpay.controller;

import com.example.labpay.dto.request.BindCardRequest;
import com.example.labpay.dto.request.Confirm3dsRequest;
import com.example.labpay.dto.response.BindCardResultResponse;
import com.example.labpay.dto.response.CardResponse;
import com.example.labpay.dto.response.ListResponse;
import com.example.labpay.service.CardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Привязка и управление картами")
public class CardController {

    private final CardService cardService;

    @PostMapping("/bind")
    @ResponseStatus(HttpStatus.CREATED)
    public BindCardResultResponse bind(Authentication auth, @Valid @RequestBody BindCardRequest request) {
        return cardService.bindCard(auth.getName(), request);
    }

    @PostMapping("/confirm-3ds")
    public CardResponse confirm3ds(Authentication auth, @Valid @RequestBody Confirm3dsRequest request) {
        return cardService.confirm3ds(auth.getName(), request);
    }

    @GetMapping
    public ListResponse<CardResponse> list(Authentication auth) {
        return new ListResponse<>(cardService.getUserCards(auth.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        cardService.deleteCard(auth.getName(), id);
    }
}