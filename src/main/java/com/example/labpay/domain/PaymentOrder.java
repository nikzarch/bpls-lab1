package com.example.labpay.domain;

import com.example.labpay.domain.user.AppUser;
import com.example.labpay.domain.widget.ProductOffer;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private ProductOffer product;

    @ManyToOne(optional = false)
    @JoinColumn(name = "buyer_id")
    private AppUser buyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, unique = true)
    private String externalOrderId;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant paidAt;
}
