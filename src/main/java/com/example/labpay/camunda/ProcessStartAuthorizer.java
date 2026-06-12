package com.example.labpay.camunda;

import com.example.labpay.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProcessStartAuthorizer {

    private final RepositoryService repositoryService;

    public void assertCanStart(String processKey, Set<String> userGroups) {
        Set<String> allowed = allowedStarterGroups(processKey);
        if (allowed.isEmpty()) {
            return;
        }
        boolean permitted = userGroups.stream().anyMatch(allowed::contains);
        if (!permitted) {
            throw new BusinessException("Not allowed to start process " + processKey);
        }
    }

    public Set<String> allowedStarterGroups(String processKey) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();

        if (definition == null) {
            throw new BusinessException("Unknown process: " + processKey);
        }

        List<org.camunda.bpm.engine.identity.Group> identityLinks =
                java.util.Collections.emptyList();

        Set<String> groups = new LinkedHashSet<>();
        for (org.camunda.bpm.engine.task.IdentityLink link :
                repositoryService.getIdentityLinksForProcessDefinition(definition.getId())) {
            if (link.getGroupId() != null) {
                groups.add(link.getGroupId());
            }
        }
        return groups;
    }
}