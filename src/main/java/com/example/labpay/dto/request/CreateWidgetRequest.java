package com.example.labpay.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWidgetRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String callbackUrl
) {}