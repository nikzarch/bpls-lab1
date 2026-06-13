package com.example.labpay.config;

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.spring.boot.starter.configuration.CamundaProcessEngineConfiguration;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamundaConfig implements CamundaProcessEngineConfiguration {

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        configuration.setHistory("full");
        configuration.setDatabaseSchemaUpdate("true");
        configuration.setJobExecutorActivate(true);
        configuration.setAuthorizationEnabled(true);
        configuration.setAuthorizationEnabledForCustomCode(false);
        configuration.setDefaultNumberOfRetries(3);
        configuration.setInitializeTelemetry(false);
    }
}