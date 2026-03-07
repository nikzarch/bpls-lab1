package com.example.labpay.service.impl;

import com.example.labpay.domain.user.AppUser;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.repository.AppUserRepository;
import com.example.labpay.repository.WalletRepository;
import com.example.labpay.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final AppUserRepository appUserRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AppUser getByUsername(String username) {
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}