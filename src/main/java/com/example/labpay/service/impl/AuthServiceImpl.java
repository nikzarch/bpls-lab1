package com.example.labpay.service.impl;

import com.example.labpay.camunda.identity.CamundaIdentitySyncService;
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
import com.example.labpay.transaction.TransactionManagerFacade;
import com.example.labpay.transaction.TransactionOptions;
import com.example.labpay.xml.XmlAppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Qualifier("XmlAppUserRepository")
    private final AppUserRepository appUserRepository;

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository;
    private final TransactionManagerFacade transactionManagerFacade;
    private final CamundaIdentitySyncService camundaIdentitySyncService;

    @Override
    public AuthResponse register(RegisterRequest request) {
        XmlAppUser user = transactionManagerFacade.execute(
                TransactionOptions.defaults("register-user-transaction"),
                () -> {
                    if (appUserRepository.findByUsername(request.username()).isPresent()) {
                        throw new BusinessException("Username already exists");
                    }

                    Role role = request.role() == null ? Role.CUSTOMER : request.role();

                    XmlAppUser savedUser = appUserRepository.save(XmlAppUser.builder()
                            .username(request.username())
                            .passwordHash(passwordEncoder.encode(request.password()))
                            .role(role.toString())
                            .build());

                    walletRepository.save(Wallet.builder()
                            .userId(savedUser.getId())
                            .build());

                    camundaIdentitySyncService.syncRegisteredUser(savedUser, request.password(), role);

                    return savedUser;
                },
                committedUser -> log.info("User {} successfully registered", committedUser.getUsername()),
                ex -> log.error("Register transaction rolled back for username {}: {}", request.username(), ex.getMessage())
        );

        return new AuthResponse(jwtService.generateToken(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        XmlAppUser user = appUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Invalid credentials");
        }

        if (walletRepository.findByUserId(user.getId()).isEmpty()) {
            walletRepository.save(Wallet.builder().userId(user.getId()).build());
        }

        return new AuthResponse(jwtService.generateToken(user));
    }
}
