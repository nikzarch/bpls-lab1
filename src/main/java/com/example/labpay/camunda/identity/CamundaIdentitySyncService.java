package com.example.labpay.camunda.identity;

import com.example.labpay.domain.user.Role;
import com.example.labpay.xml.XmlAppUser;

public interface CamundaIdentitySyncService {
    void ensureRoleGroups();
    void ensureRoleAuthorizations();
    void syncRegisteredUser(XmlAppUser user, String rawPassword);
    void syncRegisteredUser(XmlAppUser user, String rawPassword, Role role);
}