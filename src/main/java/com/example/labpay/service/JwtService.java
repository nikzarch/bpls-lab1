package com.example.labpay.service;


import com.example.labpay.xml.XmlAppUser;

public interface JwtService {
    String generateToken(XmlAppUser user);
    String extractUsername(String token);
    boolean isValid(String token);
}