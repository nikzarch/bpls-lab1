package com.example.labpay.service.impl;

import com.example.labpay.domain.card.BankCard;
import com.example.labpay.domain.card.CardStatus;
import com.example.labpay.domain.user.AppUser;
import com.example.labpay.domain.wallet.TransactionType;
import com.example.labpay.domain.wallet.Wallet;
import com.example.labpay.domain.wallet.WalletTransaction;
import com.example.labpay.dto.request.TopUpRequest;
import com.example.labpay.dto.response.TransactionResponse;
import com.example.labpay.dto.response.WalletResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.repository.BankCardRepository;
import com.example.labpay.repository.WalletRepository;
import com.example.labpay.repository.WalletTransactionRepository;
import com.example.labpay.service.BankClient;
import com.example.labpay.service.UserService;
import com.example.labpay.service.WalletService;
import com.example.labpay.util.CardTokenizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final BankCardRepository bankCardRepository;
    private final UserService userService;
    private final BankClient bankClient;
    private final CardTokenizer cardTokenizer;

    @Override
    public WalletResponse getWallet(String username) {
        AppUser user = userService.getByUsername(username);
        Wallet wallet = getWalletByUserId(user.getId());
        return new WalletResponse(wallet.getId(), wallet.getBalance().setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    @Transactional
    public WalletResponse topUp(String username, TopUpRequest request) {
        AppUser user = userService.getByUsername(username);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);

        BankCard card = bankCardRepository.findByToken(request.cardToken())
                .filter(c -> c.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Card not found"));

        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessException("Card is not active");
        }

        String cardNumber = cardTokenizer.decrypt(card.getEncryptedCardNumber());
        bankClient.directCharge(cardNumber, amount.doubleValue());

        credit(user.getId(), amount, UUID.randomUUID().toString(),
                "Top-up from card " + card.getMaskedCardNumber(), TransactionType.WALLET_TOP_UP);

        Wallet wallet = getWalletByUserId(user.getId());
        return new WalletResponse(wallet.getId(), wallet.getBalance().setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    public List<TransactionResponse> getTransactions(String username) {
        AppUser user = userService.getByUsername(username);
        Wallet wallet = getWalletByUserId(user.getId());
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(t -> new TransactionResponse(t.getId(), t.getType(),
                        t.getAmount().setScale(2, RoundingMode.HALF_UP), t.getDescription(), t.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void debit(Long userId, BigDecimal amount, String operationId, String description, TransactionType type) {
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        Wallet wallet = walletRepository.findByOwnerIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("Insufficient funds");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .wallet(wallet)
                .operationId(operationId)
                .type(type)
                .amount(amount.negate())
                .description(description)
                .createdAt(Instant.now())
                .build());
    }

    @Override
    @Transactional
    public void credit(Long userId, BigDecimal amount, String operationId, String description, TransactionType type) {
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        Wallet wallet = walletRepository.findByOwnerIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .wallet(wallet)
                .operationId(operationId)
                .type(type)
                .amount(amount)
                .description(description)
                .createdAt(Instant.now())
                .build());
    }

    private Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByOwnerId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));
    }
}