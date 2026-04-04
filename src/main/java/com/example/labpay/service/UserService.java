package com.example.labpay.service;

import com.example.labpay.xml.XmlAppUser;

public interface UserService {
     XmlAppUser getByUsername(String username);

    }
