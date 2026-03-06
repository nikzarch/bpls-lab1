package com.example.labpay.domain.widget;

import com.example.labpay.domain.user.AppUser;
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

    @ManyToOne(optional = false)
    @JoinColumn(name = "merchant_id")
    private AppUser merchant;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String callbackUrl;
}
