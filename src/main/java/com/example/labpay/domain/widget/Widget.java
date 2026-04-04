package com.example.labpay.domain.widget;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "widgets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Widget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,name = "merchant_id")
    private Long merchantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String callbackUrl;
}
