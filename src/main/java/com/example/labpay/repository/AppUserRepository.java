package com.example.labpay.repository;

import com.example.labpay.xml.XmlAppUser;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository {
    Optional<XmlAppUser> findByUsername(String username);
    Optional<XmlAppUser> findById(Long id);
    List<XmlAppUser> findAll();
    XmlAppUser save(XmlAppUser user);
}