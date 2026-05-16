package com.example.labpay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@Configuration
public class BatchConfig {

    @Bean
    @Primary
    public PlatformTransactionManager batchTransactionManager(
            @Qualifier("transactionManager") PlatformTransactionManager jtaTransactionManager) {
        return jtaTransactionManager;
    }
}