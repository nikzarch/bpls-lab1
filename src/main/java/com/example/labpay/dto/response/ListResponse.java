package com.example.labpay.dto.response;

import java.util.List;

public record ListResponse<T>(List<T> items) {}