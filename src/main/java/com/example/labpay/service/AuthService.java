package com.example.labpay.service;

import com.example.labpay.dto.request.LoginRequest;
import com.example.labpay.dto.request.RegisterRequest;
import com.example.labpay.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse register(RegisterRequest request);
}