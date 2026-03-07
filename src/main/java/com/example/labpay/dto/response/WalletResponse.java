package com.example.labpay.dto.response;

import java.math.BigDecimal;

public record WalletResponse(Long id, BigDecimal balance) {}