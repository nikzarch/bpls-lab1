package com.example.labpay.domain.card;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "card_binding_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CardBindingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionId;

    @Column(nullable = false,name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String encryptedCardNumber;

    @Column(nullable = false)
    private String holderName;

    @Column(nullable = false)
    private String maskedCardNumber;

    @Column(nullable = false)
    private String confirmationCode;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;
}