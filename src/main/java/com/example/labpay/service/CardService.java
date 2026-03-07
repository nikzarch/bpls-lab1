package com.example.labpay.service;

import com.example.labpay.dto.request.BindCardRequest;
import com.example.labpay.dto.request.Confirm3dsRequest;
import com.example.labpay.dto.response.BindCardResultResponse;
import com.example.labpay.dto.response.CardResponse;

import java.util.List;

public interface CardService {
    BindCardResultResponse bindCard(String username, BindCardRequest request);
    CardResponse confirm3ds(String username, Confirm3dsRequest request);
    List<CardResponse> getUserCards(String username);
    void deleteCard(String username, Long cardId);
}