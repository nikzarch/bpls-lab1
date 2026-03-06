package com.example.labpay.mapper;

import com.example.labpay.domain.user.AppUser;
import com.example.labpay.dto.response.UserResponse;

public class UserMapper {

    public static UserResponse toDto(AppUser appUser){
            return new UserResponse(appUser.getId(),appUser.getUsername(),appUser.getRole());
    }
}
