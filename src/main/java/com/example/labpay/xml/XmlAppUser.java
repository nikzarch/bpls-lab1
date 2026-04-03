package com.example.labpay.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlAppUser {

    @XmlElement
    private Long id;

    @XmlElement
    private String username;

    @XmlElement
    private String passwordHash;

    @XmlElement
    private String role;
}