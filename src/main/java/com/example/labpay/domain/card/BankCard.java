package com.example.labpay.domain.card;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bank_cards")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BankCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String maskedCardNumber;

    @Column(nullable = false)
    private String holderName;

    @Column(nullable = false)
    private String encryptedCardNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;
}