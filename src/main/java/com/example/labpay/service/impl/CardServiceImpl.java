package com.example.labpay.service.impl;

import com.example.labpay.domain.card.BankCard;
import com.example.labpay.domain.card.CardBindingSession;
import com.example.labpay.domain.card.CardStatus;
import com.example.labpay.domain.user.AppUser;
import com.example.labpay.dto.request.BindCardRequest;
import com.example.labpay.dto.request.Confirm3dsRequest;
import com.example.labpay.dto.response.BindCardResultResponse;
import com.example.labpay.dto.response.CardResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.repository.BankCardRepository;
import com.example.labpay.repository.CardBindingSessionRepository;
import com.example.labpay.service.BankClient;
import com.example.labpay.service.CardService;
import com.example.labpay.service.UserService;
import com.example.labpay.util.CardTokenizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final BankCardRepository bankCardRepository;
    private final CardBindingSessionRepository sessionRepository;
    private final UserService userService;
    private final CardTokenizer cardTokenizer;
    private final BankClient bankClient;

    @Override
    @Transactional
    public BindCardResultResponse bindCard(String username, BindCardRequest request) {
        AppUser user = userService.getByUsername(username);
        String digits = request.cardNumber().replaceAll("\\s+", "");

        if (!CardTokenizer.isValidLuhn(digits)) {
            throw new BusinessException("Invalid card number (Luhn check failed)");
        }
        if (request.holderName() == null || request.holderName().isBlank()) {
            throw new BusinessException("Holder name is required");
        }

        String bankSessionId = bankClient.initiateBind(digits);

        CardBindingSession session = sessionRepository.save(CardBindingSession.builder()
                .sessionId(bankSessionId)
                .user(user)
                .encryptedCardNumber(cardTokenizer.encrypt(digits))
                .holderName(request.holderName())
                .maskedCardNumber(CardTokenizer.maskCardNumber(digits))
                .confirmationCode("")
                .confirmed(false)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .build());

        return new BindCardResultResponse(true, session.getSessionId(), null, null);
    }

    @Override
    @Transactional
    public CardResponse confirm3ds(String username, Confirm3dsRequest request) {
        CardBindingSession session = sessionRepository.findBySessionId(request.sessionId())
                .orElseThrow(() -> new BusinessException("Session not found"));

        if (session.isConfirmed()) {
            throw new BusinessException("Session already confirmed");
        }
        if (Instant.now().isAfter(session.getExpiresAt())) {
            throw new BusinessException("Session expired");
        }

        AppUser user = userService.getByUsername(username);
        if (!session.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Session does not belong to user");
        }

        bankClient.confirm3ds(request.sessionId(), request.code());

        session.setConfirmed(true);
        sessionRepository.save(session);

        String cardNumber = cardTokenizer.decrypt(session.getEncryptedCardNumber());
        BankCard card = saveCard(user, cardNumber, session.getHolderName());
        return toResponse(card);
    }

    @Override
    public List<CardResponse> getUserCards(String username) {
        AppUser user = userService.getByUsername(username);
        return bankCardRepository.findByOwnerId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCard(String username, Long cardId) {
        AppUser user = userService.getByUsername(username);
        BankCard card = bankCardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException("Card not found"));
        if (!card.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Card does not belong to user");
        }
        bankCardRepository.delete(card);
    }

    private BankCard saveCard(AppUser user, String cardNumber, String holderName) {
        return bankCardRepository.save(BankCard.builder()
                .owner(user)
                .token(CardTokenizer.generateToken())
                .maskedCardNumber(CardTokenizer.maskCardNumber(cardNumber))
                .holderName(holderName)
                .status(CardStatus.ACTIVE)
                .build());
    }

    private CardResponse toResponse(BankCard card) {
        return new CardResponse(card.getId(), card.getMaskedCardNumber(), card.getHolderName(), card.getStatus(), card.getToken());
    }
}