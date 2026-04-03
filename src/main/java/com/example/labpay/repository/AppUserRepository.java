package com.example.labpay.repository;

import com.example.labpay.domain.user.AppUser;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findById(Long id);
    List<AppUser> findAll();
    AppUser save(AppUser user);
}