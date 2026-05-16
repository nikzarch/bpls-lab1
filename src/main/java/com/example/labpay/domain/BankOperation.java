package com.example.labpay.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bank_operations", indexes = {
        @Index(name = "idx_bank_op_corr", columnList = "correlationId", unique = true),
        @Index(name = "idx_bank_op_status", columnList = "status"),
        @Index(name = "idx_bank_op_status_updated", columnList = "status,updatedAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankOperationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankOperationStatus status;

    @Column(nullable = false)
    private Long userId;

    @Column
    private Long relatedOrderId;

    @Column
    private String cardToken;

    @Column(nullable = false)
    private String maskedCard;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 1000)
    private String lastError;
}