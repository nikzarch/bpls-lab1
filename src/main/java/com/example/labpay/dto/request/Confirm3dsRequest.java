package com.example.labpay.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record Confirm3dsRequest(
        @NotBlank
        String sessionId,

        @NotBlank @Pattern(regexp = "^\\d{4,6}$", message = "must be 4-6 digits")
        String code
) {}