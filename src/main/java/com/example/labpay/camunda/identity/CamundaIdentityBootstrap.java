package com.example.labpay.camunda.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CamundaIdentityBootstrap implements ApplicationRunner {

    private final CamundaIdentitySyncService camundaIdentitySyncService;

    @Override
    public void run(ApplicationArguments args) {
        camundaIdentitySyncService.ensureRoleGroups();
    }
}
