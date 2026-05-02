package com.example.labpay.config;

import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

@Configuration
@EnableJms
public class JmsConfig {

    @Value("${app.jms.url:amqp://admin:admin@localhost:5672}")
    private String url;

    @Bean
    public ConnectionFactory connectionFactory() {
        return new JmsConnectionFactory(url);
    }

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter c = new MappingJackson2MessageConverter();
        c.setTargetType(MessageType.TEXT);
        c.setTypeIdPropertyName("_type");
        return c;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory cf, MessageConverter mc) {
        JmsTemplate t = new JmsTemplate(cf);
        t.setMessageConverter(mc);
        t.setReceiveTimeout(10_000);
        return t;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory cf, MessageConverter mc) {
        DefaultJmsListenerContainerFactory f = new DefaultJmsListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(mc);
        f.setConcurrency("2-5");
        f.setSessionTransacted(true);
        return f;
    }
}