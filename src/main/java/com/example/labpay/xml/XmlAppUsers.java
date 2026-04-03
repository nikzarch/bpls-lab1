package com.example.labpay.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@XmlRootElement(name = "users")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlAppUsers {

    @XmlElement(name = "user")
    private List<XmlAppUser> users = new ArrayList<>();
}