package com.example.labpay.service;


import com.example.labpay.domain.user.AppUser;

public interface JwtService {
    String generateToken(AppUser user);
    String extractUsername(String token);
    boolean isValid(String token);
}