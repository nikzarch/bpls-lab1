package com.example.labpay.camunda.identity;

import com.example.labpay.domain.user.Role;
import com.example.labpay.xml.XmlAppUser;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CamundaIdentitySyncServiceImpl implements CamundaIdentitySyncService {

    private static final String GROUP_TYPE = "WORKFLOW_ROLE";

    private final IdentityService identityService;

    @Override
    @Transactional
    public void ensureRoleGroups() {
        for (Role role : Role.values()) {
            ensureGroup(role);
        }
    }

    @Override
    @Transactional
    public void syncRegisteredUser(XmlAppUser user, String rawPassword) {
        Role role = Role.valueOf(user.getRole());
        syncRegisteredUser(user, rawPassword, role);
    }

    @Override
    @Transactional
    public void syncRegisteredUser(XmlAppUser user, String rawPassword, Role role) {
        ensureRoleGroups();
        ensureGroup(role);
        upsertUser(user, rawPassword, role);
        syncMemberships(user.getUsername(), role);
    }

    private Group ensureGroup(Role role) {
        Group group = identityService.createGroupQuery()
                .groupId(role.name())
                .singleResult();

        if (group == null) {
            group = identityService.newGroup(role.name());
            group.setName(role.name());
            group.setType(GROUP_TYPE);
            identityService.saveGroup(group);
        } else {
            boolean dirty = false;
            if (!Objects.equals(group.getName(), role.name())) {
                group.setName(role.name());
                dirty = true;
            }
            if (!Objects.equals(group.getType(), GROUP_TYPE)) {
                group.setType(GROUP_TYPE);
                dirty = true;
            }
            if (dirty) {
                identityService.saveGroup(group);
            }
        }

        return group;
    }

    private void upsertUser(XmlAppUser user, String rawPassword, Role role) {
        User camundaUser = identityService.createUserQuery()
                .userId(user.getUsername())
                .singleResult();

        if (camundaUser == null) {
            camundaUser = identityService.newUser(user.getUsername());
        }

        camundaUser.setFirstName(user.getUsername());
        camundaUser.setLastName(role.name());
        camundaUser.setEmail(user.getUsername() + "@labpay.local");
        if (rawPassword != null && !rawPassword.isBlank()) {
            camundaUser.setPassword(rawPassword);

        }
        identityService.saveUser(camundaUser);

    }

    private void syncMemberships(String username, Role role) {
        List<Group> currentGroups = identityService.createGroupQuery()
                .groupMember(username)
                .list();

        List<String> toRemove = currentGroups.stream()
                .map(Group::getId)
                .filter(groupId -> !groupId.equals(role.name()))
                .collect(Collectors.toList());

        for (String groupId : toRemove) {
            identityService.deleteMembership(username, groupId);
        }

        boolean alreadyMember = currentGroups.stream()
                .anyMatch(group -> role.name().equals(group.getId()));

        if (!alreadyMember) {
            identityService.createMembership(username, role.name());
        }
    }
}
