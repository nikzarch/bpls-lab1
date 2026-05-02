package com.example.bankra;

import jakarta.jms.ConnectionFactory;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConfigProperty;
import jakarta.resource.spi.ConnectionDefinition;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import javax.security.auth.Subject;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.Set;

@ConnectionDefinition(
        connectionFactory = BankConnectionFactory.class,
        connectionFactoryImpl = BankConnectionFactoryImpl.class,
        connection = BankConnection.class,
        connectionImpl = BankConnectionImpl.class
)
public class BankManagedConnectionFactory implements ManagedConnectionFactory {

    private transient ConnectionFactory amqpConnectionFactory;

    @ConfigProperty(defaultValue = "15000", description = "Reply timeout in milliseconds")
    private Long timeoutMs = 15000L;

    private PrintWriter logWriter;

    @Override
    public Object createConnectionFactory(ConnectionManager cm) {
        return new BankConnectionFactoryImpl(this, cm);
    }

    @Override
    public Object createConnectionFactory() {
        return new BankConnectionFactoryImpl(this, new DefaultConnectionManager());
    }

    @Override
    public ManagedConnection createManagedConnection(
            Subject subject,
            ConnectionRequestInfo info
    ) throws ResourceException {
        if (amqpConnectionFactory == null) {
            throw new ResourceException("amqpConnectionFactory is not configured");
        }
        return new BankManagedConnection(this, amqpConnectionFactory, timeoutMs);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ManagedConnection matchManagedConnections(
            Set connections,
            Subject subject,
            ConnectionRequestInfo info
    ) throws ResourceException {
        for (Object o : connections) {
            if (o instanceof BankManagedConnection mc) {
                return mc;
            }
        }
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    public ConnectionFactory getAmqpConnectionFactory() {
        return amqpConnectionFactory;
    }

    public void setAmqpConnectionFactory(ConnectionFactory amqpConnectionFactory) {
        this.amqpConnectionFactory = amqpConnectionFactory;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BankManagedConnectionFactory that)) return false;
        return Objects.equals(timeoutMs, that.timeoutMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeoutMs);
    }
}