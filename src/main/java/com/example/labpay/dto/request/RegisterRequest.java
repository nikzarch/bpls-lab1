package com.example.labpay.dto.request;


import com.example.labpay.domain.user.Role;

public record RegisterRequest(String username, String password, Role role) {
}
