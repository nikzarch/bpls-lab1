package com.example.labpay.config;

import com.example.labpay.mq.events.NotificationEvent;
import com.example.labpay.mq.events.WebhookEvent;
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

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJms
public class JmsConfig {

    @Value("${app.jms.url:${APP_JMS_URL:amqp://localhost:5672}}")
    private String url;

    @Value("${app.jms.username:admin}")
    private String username;

    @Value("${app.jms.password:admin}")
    private String password;

    @Bean
    public ConnectionFactory connectionFactory() {
        return new JmsConnectionFactory(username, password, url);
    }

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();

        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");

        Map<String, Class<?>> typeIdMappings = new HashMap<>();
        typeIdMappings.put("notification", NotificationEvent.class);
        typeIdMappings.put("webhook", WebhookEvent.class);
        converter.setTypeIdMappings(typeIdMappings);

        return converter;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory cf, MessageConverter mc) {
        JmsTemplate t = new JmsTemplate(cf);
        t.setMessageConverter(mc);
        t.setReceiveTimeout(5_000);
        t.setExplicitQosEnabled(true);
        t.setDeliveryPersistent(false);
        t.setTimeToLive(30_000);

        return t;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory cf,
            MessageConverter mc
    ) {
        DefaultJmsListenerContainerFactory f = new DefaultJmsListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setMessageConverter(mc);
        f.setConcurrency("2-5");
        f.setSessionTransacted(true);
        return f;
    }
}