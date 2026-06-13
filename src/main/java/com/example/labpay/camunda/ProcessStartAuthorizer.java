package com.example.labpay.camunda;

import com.example.labpay.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProcessStartAuthorizer {

    private static final String ROLE_ADMIN = "ADMIN";

    private final RepositoryService repositoryService;
    private final AuthorizationService authorizationService;
    private final CamundaSystemContext systemContext;

    public void assertCanStart(String processKey, Set<String> userGroups) {
        if (userGroups.contains(ROLE_ADMIN)) {
            assertDefinitionExists(processKey);
            return;
        }

        assertDefinitionExists(processKey);

        if (!canStart(userGroups, processKey)) {
            throw new BusinessException("Not allowed to start process " + processKey);
        }
    }

    public boolean canStart(Set<String> userGroups, String processKey) {
        if (userGroups.contains(ROLE_ADMIN)) {
            return true;
        }
        return systemContext.call(() -> {
            for (String group : userGroups) {
                boolean allowed = authorizationService.isUserAuthorized(
                        null,
                        List.of(group),
                        ProcessDefinitionPermissions.CREATE_INSTANCE,
                        Resources.PROCESS_DEFINITION,
                        processKey
                );
                if (allowed) {
                    return true;
                }
            }
            return false;
        });
    }

    private void assertDefinitionExists(String processKey) {
        ProcessDefinition definition = systemContext.call(() ->
                repositoryService.createProcessDefinitionQuery()
                        .processDefinitionKey(processKey)
                        .latestVersion()
                        .singleResult()
        );
        if (definition == null) {
            throw new BusinessException("Unknown process: " + processKey);
        }
    }
}