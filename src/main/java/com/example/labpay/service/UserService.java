package com.example.labpay.service;

import com.example.labpay.domain.user.AppUser;
import com.example.labpay.dto.request.RegisterRequest;

public interface UserService {
     AppUser register(RegisterRequest request);
     AppUser getByUsername(String username);

    }
