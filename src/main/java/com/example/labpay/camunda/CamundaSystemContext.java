package com.example.labpay.camunda;

import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.IdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CamundaSystemContext {

    private static final List<String> CAMUNDA_ADMIN_GROUPS = List.of("camunda-admin");

    private final IdentityService identityService;

    @Value("${camunda.bpm.admin-user.id:admin}")
    private String camundaAdminId;

    public <T> T call(Supplier<T> action) {
        identityService.setAuthentication(camundaAdminId, CAMUNDA_ADMIN_GROUPS);
        try {
            return action.get();
        } finally {
            identityService.clearAuthentication();
        }
    }

    public void run(Runnable action) {
        identityService.setAuthentication(camundaAdminId, CAMUNDA_ADMIN_GROUPS);
        try {
            action.run();
        } finally {
            identityService.clearAuthentication();
        }
    }
}