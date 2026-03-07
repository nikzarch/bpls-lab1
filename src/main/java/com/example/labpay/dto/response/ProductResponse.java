package com.example.labpay.dto.response;

import com.example.labpay.domain.widget.ProductType;
import java.math.BigDecimal;

public record ProductResponse(Long id, String title, ProductType type, BigDecimal price, String description) {}