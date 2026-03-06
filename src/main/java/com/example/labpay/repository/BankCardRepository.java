package com.example.labpay.repository;

import com.example.labpay.domain.card.BankCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankCardRepository extends JpaRepository<BankCard, Long> {
    List<BankCard> findByOwnerId(Long ownerId);
    Optional<BankCard> findByToken(String token);
}
