package com.example.labpay.controller;

import com.example.labpay.dto.request.LoginRequest;
import com.example.labpay.dto.request.RegisterRequest;
import com.example.labpay.dto.response.AuthResponse;
import com.example.labpay.dto.response.UserResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.mapper.UserMapper;
import com.example.labpay.service.AuthService;
import com.example.labpay.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.api.ErrorMessage;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @ExceptionHandler(BusinessException.class)
    public ErrorMessage handleBusinessException(BusinessException exc) {
        return new ErrorMessage(exc.getMessage());
    }
}
