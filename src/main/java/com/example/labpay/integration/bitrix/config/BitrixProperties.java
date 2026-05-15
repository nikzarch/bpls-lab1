package com.example.labpay.integration.bitrix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bitrix")
public record BitrixProperties(
        String baseUrl,
        String externalOrderField,
        String sourceId,
        String stageId,
        String currencyId,
        Integer responsibleUserId,
        int timeoutMs
) {
}