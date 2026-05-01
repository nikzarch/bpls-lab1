package com.example.labpay.config;


import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;

@Configuration
@EnableJms
public class JmsConfig {

    @Bean
    public ConnectionFactory connectionFactory() {
        return new JmsConnectionFactory(
                "amqp://admin:admin@localhost:5672"
        );
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory cf) {
        return new JmsTemplate(cf);
    }

}