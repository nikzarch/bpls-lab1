package com.example.labpay.config;

import com.example.bankra.BankConnectionFactory;
import com.example.bankra.BankManagedConnectionFactory;
import jakarta.jms.ConnectionFactory;
import jakarta.resource.ResourceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jca.support.LocalConnectionFactoryBean;

@Configuration
public class BankJcaConfig {

    @Value("${app.bank.timeout-ms:15000}")
    private Long timeoutMs;

    @Bean
    public BankManagedConnectionFactory bankManagedConnectionFactory(ConnectionFactory amqpConnectionFactory) {
        BankManagedConnectionFactory mcf = new BankManagedConnectionFactory();
        mcf.setAmqpConnectionFactory(amqpConnectionFactory);
        mcf.setTimeoutMs(timeoutMs);
        return mcf;
    }

    @Bean
    public LocalConnectionFactoryBean bankConnectionFactoryBean(BankManagedConnectionFactory mcf) {
        LocalConnectionFactoryBean bean = new LocalConnectionFactoryBean();
        bean.setManagedConnectionFactory(mcf);
        return bean;
    }

    @Bean
    public BankConnectionFactory bankConnectionFactory(LocalConnectionFactoryBean bean) throws ResourceException {
        Object cf = bean.getObject();
        if (!(cf instanceof BankConnectionFactory)) {
            throw new ResourceException("Unexpected connection factory type");
        }
        return (BankConnectionFactory) cf;
    }
}