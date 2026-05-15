package com.example.labpay.integration.bitrix.config;

import com.example.labpay.integration.bitrix.ra.BitrixConnectionFactory;
import com.example.labpay.integration.bitrix.ra.BitrixConnectionFactoryImpl;
import com.example.labpay.integration.bitrix.ra.BitrixManagedConnectionFactory;
import com.example.labpay.integration.bitrix.ra.BitrixSimpleConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BitrixProperties.class)
public class BitrixJcaConfiguration {

    @Bean
    public RestClient bitrixRestClient(BitrixProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .build();
    }

    @Bean
    public BitrixManagedConnectionFactory bitrixManagedConnectionFactory(
            BitrixProperties props,
            RestClient bitrixRestClient,
            ObjectMapper objectMapper
    ) {
        return new BitrixManagedConnectionFactory(
                bitrixRestClient,
                objectMapper,
                props.externalOrderField() != null ? props.externalOrderField() : "UF_CRM_EXTERNAL_ORDER_ID",
                props.sourceId() != null ? props.sourceId() : "WEB",
                props.stageId(),
                props.currencyId() != null ? props.currencyId() : "RUB",
                props.responsibleUserId(),
                props.timeoutMs() > 0 ? props.timeoutMs() : 10_000
        );
    }

    @Bean
    public BitrixConnectionFactory bitrixConnectionFactory(BitrixManagedConnectionFactory mcf) {
        return new BitrixConnectionFactoryImpl(mcf, new BitrixSimpleConnectionManager());
    }
}