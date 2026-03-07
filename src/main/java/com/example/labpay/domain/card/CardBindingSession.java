package com.example.labpay.domain.card;

import com.example.labpay.domain.user.AppUser;
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

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

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