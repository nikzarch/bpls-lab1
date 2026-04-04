package com.example.labpay.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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