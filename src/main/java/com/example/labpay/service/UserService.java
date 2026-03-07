package com.example.labpay.service;

import com.example.labpay.domain.user.AppUser;

public interface UserService {
     AppUser getByUsername(String username);

    }
