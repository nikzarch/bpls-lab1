package com.example.labpay.domain.card;

import com.example.labpay.domain.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bank_cards")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BankCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser owner;

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