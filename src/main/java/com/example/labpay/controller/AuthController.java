package com.example.labpay.controller;

import com.example.labpay.dto.request.RegisterRequest;
import com.example.labpay.dto.response.UserResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.mapper.UserMapper;
import com.example.labpay.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.api.ErrorMessage;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/register")
    public UserResponse register(@RequestBody RegisterRequest request) {
        return UserMapper.toDto(userService.register(request));
    }

    @ExceptionHandler(BusinessException.class)
    public ErrorMessage handleBusinessException(BusinessException exc){
        return new ErrorMessage(exc.getMessage());
    }
}
