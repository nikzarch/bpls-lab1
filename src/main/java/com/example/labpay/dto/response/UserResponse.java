package com.example.labpay.dto.response;


import com.example.labpay.domain.user.Role;

public record UserResponse(Long id, String username, Role role) {
}
