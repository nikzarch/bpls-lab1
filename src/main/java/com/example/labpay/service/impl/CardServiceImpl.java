package com.example.labpay.service.impl;

import com.example.labpay.domain.card.BankCard;
import com.example.labpay.domain.card.CardBindingSession;
import com.example.labpay.domain.card.CardStatus;
import com.example.labpay.dto.request.BindCardRequest;
import com.example.labpay.dto.request.Confirm3dsRequest;
import com.example.labpay.dto.response.BindCardResultResponse;
import com.example.labpay.dto.response.CardResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.repository.BankCardRepository;
import com.example.labpay.repository.CardBindingSessionRepository;
import com.example.labpay.service.BankClient;
import com.example.labpay.service.CardService;
import com.example.labpay.service.UserService;
import com.example.labpay.transaction.TransactionManagerFacade;
import com.example.labpay.transaction.TransactionOptions;
import com.example.labpay.util.CardTokenizer;
import com.example.labpay.xml.XmlAppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final BankCardRepository bankCardRepository;
    private final CardBindingSessionRepository sessionRepository;
    private final UserService userService;
    private final CardTokenizer cardTokenizer;
    private final BankClient bankClient;
    private final TransactionManagerFacade transactionManagerFacade;

    @Override
    public BindCardResultResponse bindCard(String username, BindCardRequest request) {
        validateRequest(username, request);
        String bankSessionId = callBankInitiateBind(request);
        return persistBindingSession(username, request, bankSessionId);
    }

    @Override
    public String callBankInitiateBind(BindCardRequest request) {
        String digits = request.cardNumber().replaceAll("\\s+", "");

        if (!CardTokenizer.isValidLuhn(digits)) {
            throw new BusinessException("Invalid card number");
        }

        String[] expiryParts = request.expiryDate().split("/");
        int month = Integer.parseInt(expiryParts[0]);
        int year = 2000 + Integer.parseInt(expiryParts[1]);
        if (YearMonth.of(year, month).isBefore(YearMonth.now())) {
            throw new BusinessException("Card is expired");
        }

        String bankSessionId = bankClient.initiateBind(digits, request.cvv(), request.expiryDate());

        if (bankSessionId == null || bankSessionId.isBlank()) {
            throw new BusinessException("Bank did not return 3DS session id");
        }

        return bankSessionId;
    }

    @Override
    public BindCardResultResponse persistBindingSession(String username, BindCardRequest request, String bankSessionId) {
        return transactionManagerFacade.execute(
                TransactionOptions.defaults("bind-card-persist-transaction"),
                () -> {
                    XmlAppUser user = userService.getByUsername(username);
                    String digits = request.cardNumber().replaceAll("\\s+", "");
                    String masked = CardTokenizer.maskCardNumber(digits);

                    if (bankCardRepository.existsByUserIdAndMaskedCardNumber(user.getId(), masked)) {
                        throw new BusinessException("Card already bound");
                    }

                    sessionRepository.save(CardBindingSession.builder()
                            .sessionId(bankSessionId)
                            .userId(user.getId())
                            .encryptedCardNumber(cardTokenizer.encrypt(digits))
                            .holderName(request.holderName())
                            .maskedCardNumber(masked)
                            .confirmationCode("")
                            .confirmed(false)
                            .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                            .createdAt(Instant.now())
                            .build());

                    return new BindCardResultResponse(true, bankSessionId, null, null);
                },
                result -> log.info("Card binding session persisted for user {}", username),
                ex -> log.error("Persist binding session rolled back for user {}: {}", username, ex.getMessage())
        );
    }

    @Override
    public CardResponse confirm3ds(String username, Confirm3dsRequest request) {
        callBankConfirm3ds(request.sessionId(), request.code());
        return persistConfirmedCard(username, request.sessionId());
    }

    @Override
    public void callBankConfirm3ds(String sessionId, String code) {
        bankClient.confirm3ds(sessionId, code);
    }

    @Override
    public CardResponse persistConfirmedCard(String username, String sessionId) {
        return transactionManagerFacade.execute(
                TransactionOptions.defaults("confirm-3ds-persist-transaction"),
                () -> {
                    CardBindingSession session = sessionRepository.findBySessionId(sessionId)
                            .orElseThrow(() -> new NotFoundException("Session not found"));

                    if (session.isConfirmed()) {
                        throw new BusinessException("Session already confirmed");
                    }
                    if (Instant.now().isAfter(session.getExpiresAt())) {
                        throw new BusinessException("Session expired");
                    }

                    XmlAppUser user = userService.getByUsername(username);
                    if (!session.getUserId().equals(user.getId())) {
                        throw new BusinessException("Session does not belong to user");
                    }

                    session.setConfirmed(true);
                    sessionRepository.save(session);

                    String cardNumber = cardTokenizer.decrypt(session.getEncryptedCardNumber());
                    String masked = CardTokenizer.maskCardNumber(cardNumber);

                    if (bankCardRepository.existsByUserIdAndMaskedCardNumber(user.getId(), masked)) {
                        throw new BusinessException("Card already bound");
                    }

                    BankCard card = bankCardRepository.save(BankCard.builder()
                            .userId(user.getId())
                            .token(CardTokenizer.generateToken())
                            .maskedCardNumber(masked)
                            .holderName(session.getHolderName())
                            .encryptedCardNumber(session.getEncryptedCardNumber())
                            .status(CardStatus.ACTIVE)
                            .build());

                    return toResponse(card);
                },
                result -> log.info("3DS confirmed and card persisted for user {}", username),
                ex -> log.error("Persist confirmed card rolled back for user {}: {}", username, ex.getMessage())
        );
    }

    @Override
    public List<CardResponse> getUserCards(String username) {
        XmlAppUser user = userService.getByUsername(username);
        return bankCardRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void deleteCard(String username, Long cardId) {
        transactionManagerFacade.execute(
                TransactionOptions.defaults("delete-card-transaction"),
                () -> {
                    XmlAppUser user = userService.getByUsername(username);
                    BankCard card = bankCardRepository.findById(cardId)
                            .orElseThrow(() -> new NotFoundException("Card not found"));

                    if (!card.getUserId().equals(user.getId())) {
                        throw new BusinessException("Card does not belong to user");
                    }

                    bankCardRepository.delete(card);
                    return null;
                },
                result -> log.info("Card {} deleted by user {}", cardId, username),
                ex -> log.error("Delete card rolled back for user {}: {}", username, ex.getMessage())
        );
    }

    private void validateRequest(String username, BindCardRequest request) {
        XmlAppUser user = userService.getByUsername(username);
        String digits = request.cardNumber().replaceAll("\\s+", "");
        String masked = CardTokenizer.maskCardNumber(digits);
        if (bankCardRepository.existsByUserIdAndMaskedCardNumber(user.getId(), masked)) {
            throw new BusinessException("Card already bound");
        }
    }

    private CardResponse toResponse(BankCard card) {
        return new CardResponse(
                card.getId(),
                card.getMaskedCardNumber(),
                card.getHolderName(),
                card.getStatus(),
                card.getToken()
        );
    }
}