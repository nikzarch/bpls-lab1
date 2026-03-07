package com.example.labpay.dto.request;

import com.example.labpay.domain.widget.ProductType;
import java.math.BigDecimal;

public record CreateProductRequest(String title, ProductType type, BigDecimal price, String description) {}