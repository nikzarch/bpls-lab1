package com.example.labpay.dto.request;

import com.example.labpay.domain.widget.ProductType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull ProductType type,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotBlank @Size(max = 1000) String description
) {}