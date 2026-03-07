package com.example.labpay.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BindCardRequest(
        @NotBlank @Size(min = 13, max = 19) @Pattern(regexp = "^\\d{13,19}$", message = "must be 13-19 digits")
        String cardNumber,

        @NotBlank @Size(min = 2, max = 100)
        String holderName,

        @NotBlank @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "must be MM/YY format")
        String expiryDate,

        @NotBlank @Pattern(regexp = "^\\d{3,4}$", message = "must be 3-4 digits")
        String cvv
) {}