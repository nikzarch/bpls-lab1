package com.example.labpay.camunda.identity;

import com.example.labpay.domain.user.Role;
import com.example.labpay.xml.XmlAppUser;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.TaskPermissions;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CamundaIdentitySyncServiceImpl implements CamundaIdentitySyncService {

    private static final String GROUP_TYPE = "WORKFLOW_ROLE";
    private static final List<String> CAMUNDA_ADMIN_GROUPS = List.of("camunda-admin");

    private static final List<String> CUSTOMER_PROCESS_KEYS = List.of(
            "card-binding-process",
            "payment-create-process",
            "payment-process",
            "transfer-process",
            "wallet-top-up-process"
    );

    private static final List<String> MAINTENANCE_PROCESS_KEYS = List.of(
            "maintenance-bank-reconciliation",
            "maintenance-hold-expiration",
            "maintenance-card-session-cleanup",
            "maintenance-stuck-transfer"
    );

    private final IdentityService identityService;
    private final AuthorizationService authorizationService;

    @Value("${camunda.bpm.admin-user.id:admin}")
    private String camundaAdminId;

    @Override
    @Transactional
    public void ensureRoleGroups() {
        runAsSystem(() -> {
            for (Role role : Role.values()) {
                ensureGroup(role);
            }
        });
    }

    @Override
    @Transactional
    public void ensureRoleAuthorizations() {
        runAsSystem(() -> {
            grantAllForGroup(Role.ADMIN.name());
            grantApplicationAccess(Role.ADMIN.name(), "tasklist", "cockpit", "admin", "welcome");

            for (String processKey : CUSTOMER_PROCESS_KEYS) {
                grantProcessStartAndRead(Role.CUSTOMER.name(), processKey);
            }
            grantTaskAccess(Role.CUSTOMER.name());
            grantApplicationAccess(Role.CUSTOMER.name(), "tasklist");

            for (String processKey : MAINTENANCE_PROCESS_KEYS) {
                revokeProcessForGroup(Role.CUSTOMER.name(), processKey);
            }
        });
    }

    private void grantApplicationAccess(String groupId, String... applications) {
        for (String application : applications) {
            upsertGrant(groupId, Resources.APPLICATION, application, Permissions.ACCESS);
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
        runAsSystem(() -> {
            ensureRoleGroupsInternal();
            ensureGroup(role);
            upsertUser(user, rawPassword, role);
            syncMemberships(user.getUsername(), role);
        });
    }

    private void runAsSystem(Runnable action) {
        identityService.setAuthentication(camundaAdminId, CAMUNDA_ADMIN_GROUPS);
        try {
            action.run();
        } finally {
            identityService.clearAuthentication();
        }
    }

    private void ensureRoleGroupsInternal() {
        for (Role role : Role.values()) {
            ensureGroup(role);
        }
    }

    private void grantAllForGroup(String groupId) {
        upsertGrant(groupId, Resources.PROCESS_DEFINITION, Authorization.ANY,
                ProcessDefinitionPermissions.ALL);
        upsertGrant(groupId, Resources.PROCESS_INSTANCE, Authorization.ANY,
                Permissions.ALL);
        upsertGrant(groupId, Resources.TASK, Authorization.ANY,
                TaskPermissions.ALL);
        upsertGrant(groupId, Resources.DEPLOYMENT, Authorization.ANY,
                Permissions.ALL);
        upsertGrant(groupId, Resources.AUTHORIZATION, Authorization.ANY,
                Permissions.ALL);
        upsertGrant(groupId, Resources.GROUP, Authorization.ANY,
                Permissions.ALL);
        upsertGrant(groupId, Resources.USER, Authorization.ANY,
                Permissions.ALL);
    }

    private void grantProcessStartAndRead(String groupId, String processKey) {
        upsertGrant(groupId, Resources.PROCESS_DEFINITION, processKey,
                ProcessDefinitionPermissions.READ,
                ProcessDefinitionPermissions.CREATE_INSTANCE,
                ProcessDefinitionPermissions.READ_INSTANCE,
                ProcessDefinitionPermissions.READ_TASK,
                ProcessDefinitionPermissions.UPDATE_TASK);
        upsertGrant(groupId, Resources.PROCESS_INSTANCE, Authorization.ANY,
                Permissions.CREATE,
                Permissions.READ);
    }

    private void grantTaskAccess(String groupId) {
        upsertGrant(groupId, Resources.TASK, Authorization.ANY,
                TaskPermissions.READ,
                TaskPermissions.UPDATE);
    }

    private void revokeProcessForGroup(String groupId, String processKey) {
        Authorization existing = authorizationService.createAuthorizationQuery()
                .groupIdIn(groupId)
                .resourceType(Resources.PROCESS_DEFINITION)
                .resourceId(processKey)
                .singleResult();

        if (existing != null && existing.getAuthorizationType() != Authorization.AUTH_TYPE_REVOKE) {
            authorizationService.deleteAuthorization(existing.getId());
            existing = null;
        }

        if (existing == null) {
            Authorization revoke = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_REVOKE);
            revoke.setGroupId(groupId);
            revoke.setResource(Resources.PROCESS_DEFINITION);
            revoke.setResourceId(processKey);
            revoke.addPermission(ProcessDefinitionPermissions.CREATE_INSTANCE);
            authorizationService.saveAuthorization(revoke);
        }
    }

    private void upsertGrant(String groupId,
                             Resources resource,
                             String resourceId,
                             Permission... permissions) {
        Authorization authorization = authorizationService.createAuthorizationQuery()
                .groupIdIn(groupId)
                .resourceType(resource)
                .resourceId(resourceId)
                .singleResult();

        if (authorization == null || authorization.getAuthorizationType() != Authorization.AUTH_TYPE_GRANT) {
            if (authorization != null) {
                authorizationService.deleteAuthorization(authorization.getId());
            }
            authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
            authorization.setGroupId(groupId);
            authorization.setResource(resource);
            authorization.setResourceId(resourceId);
        }

        for (Permission permission : permissions) {
            authorization.addPermission(permission);
        }

        authorizationService.saveAuthorization(authorization);
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
            return group;
        }

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

        for (Group group : currentGroups) {
            if (!group.getId().equals(role.name())) {
                identityService.deleteMembership(username, group.getId());
            }
        }

        boolean alreadyMember = currentGroups.stream()
                .anyMatch(group -> role.name().equals(group.getId()));

        if (!alreadyMember) {
            identityService.createMembership(username, role.name());
        }
    }
}