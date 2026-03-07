package com.example.labpay.service.impl;

import com.example.labpay.domain.user.AppUser;
import com.example.labpay.domain.user.Role;
import com.example.labpay.domain.wallet.Wallet;
import com.example.labpay.dto.request.LoginRequest;
import com.example.labpay.dto.request.RegisterRequest;
import com.example.labpay.dto.response.AuthResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.repository.AppUserRepository;
import com.example.labpay.repository.WalletRepository;
import com.example.labpay.service.AuthService;
import com.example.labpay.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new BusinessException("Username is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessException("Password is required");
        }
        if (appUserRepository.findByUsername(request.username()).isPresent()) {
            throw new BusinessException("Username already exists");
        }

        Role role = request.role() == null ? Role.CUSTOMER : request.role();
        AppUser user = appUserRepository.save(AppUser.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .build());
        walletRepository.save(Wallet.builder().owner(user).build());

        return new AuthResponse(jwtService.generateToken(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new BusinessException("Username is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessException("Password is required");
        }

        AppUser user = appUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Invalid credentials");
        }

        return new AuthResponse(jwtService.generateToken(user));
    }
}