package com.example.labpay.service.impl;


import com.example.labpay.domain.user.AppUser;
import com.example.labpay.domain.user.Role;
import com.example.labpay.domain.wallet.Wallet;
import com.example.labpay.dto.request.RegisterRequest;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.repository.AppUserRepository;
import com.example.labpay.repository.WalletRepository;
import com.example.labpay.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final AppUserRepository appUserRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AppUser register(RegisterRequest request) {
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
        walletRepository.save(Wallet.builder()
                .owner(user)
                .build());
        return user;
    }

    @Override
    public AppUser getByUsername(String username) {
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));
    }
}

