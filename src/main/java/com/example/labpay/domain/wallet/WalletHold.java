package com.example.labpay.domain.wallet;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_holds", indexes = {
        @Index(name = "idx_hold_wallet_status", columnList = "wallet_id,status"),
        @Index(name = "idx_hold_external_ref", columnList = "externalRef", unique = true)
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletHold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldStatus status;

    @Column(nullable = false, unique = true)
    private String externalRef;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant resolvedAt;
}