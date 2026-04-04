package com.example.labpay.mapper;

import com.example.labpay.domain.user.Role;
import com.example.labpay.dto.response.UserResponse;
import com.example.labpay.xml.XmlAppUser;

public class UserMapper {

    public static UserResponse toDto(XmlAppUser appUser){
            return new UserResponse(appUser.getId(),appUser.getUsername(), Role.valueOf(appUser.getRole()));
    }
}
