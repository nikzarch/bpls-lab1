package com.example.labpay.controller;

import com.example.labpay.dto.request.LoginRequest;
import com.example.labpay.dto.request.RegisterRequest;
import com.example.labpay.dto.response.AuthResponse;
import com.example.labpay.dto.response.UserResponse;
import com.example.labpay.mapper.UserMapper;
import com.example.labpay.service.AuthService;
import com.example.labpay.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация и авторизация")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        return UserMapper.toDto(userService.getByUsername(auth.getName()));
    }
}