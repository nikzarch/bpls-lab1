package com.example.labpay.integration.bitrix.ra;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.client.RestClient;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

@RequiredArgsConstructor
@Getter
@Setter
public class BitrixManagedConnectionFactory implements ManagedConnectionFactory, Serializable {

    private transient RestClient restClient;
    private transient ObjectMapper objectMapper;

    private String externalOrderField;
    private String sourceId;
    private String stageId;
    private String currencyId;
    private Integer responsibleUserId;
    private int timeoutMs = 10_000;

    private transient PrintWriter logWriter;

    public BitrixManagedConnectionFactory(
            RestClient restClient,
            ObjectMapper objectMapper,
            String externalOrderField,
            String sourceId,
            String stageId,
            String currencyId,
            Integer responsibleUserId,
            int timeoutMs
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.externalOrderField = externalOrderField;
        this.sourceId = sourceId;
        this.stageId = stageId;
        this.currencyId = currencyId;
        this.responsibleUserId = responsibleUserId;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
        if (cxManager == null) {
            throw new ResourceException("ConnectionManager must not be null");
        }
        return new BitrixConnectionFactoryImpl(this, cxManager);
    }

    @Override
    public Object createConnectionFactory() throws ResourceException {
        return new BitrixConnectionFactoryImpl(this, new BitrixSimpleConnectionManager());
    }

    @Override
    public ManagedConnection createManagedConnection(
            javax.security.auth.Subject subject,
            ConnectionRequestInfo cxRequestInfo
    ) throws ResourceException {
        if (restClient == null || objectMapper == null) {
            throw new ResourceException("Bitrix factory is not initialized");
        }

        BitrixApiClient apiClient = new BitrixApiClient(
                restClient,
                objectMapper,
                externalOrderField,
                sourceId,
                stageId,
                currencyId,
                responsibleUserId
        );
        return new BitrixManagedConnection(apiClient);
    }

    @Override
    public ManagedConnection matchManagedConnections(
            Set set,
            javax.security.auth.Subject subject,
            ConnectionRequestInfo cxRequestInfo
    ) throws ResourceException {
        if (set == null || set.isEmpty()) {
            return null;
        }
        return (ManagedConnection) set.iterator().next();
    }

    @Override
    public PrintWriter getLogWriter() throws ResourceException {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws ResourceException {
        this.logWriter = out;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BitrixManagedConnectionFactory that)) return false;
        return timeoutMs == that.timeoutMs
                && Objects.equals(externalOrderField, that.externalOrderField)
                && Objects.equals(sourceId, that.sourceId)
                && Objects.equals(stageId, that.stageId)
                && Objects.equals(currencyId, that.currencyId)
                && Objects.equals(responsibleUserId, that.responsibleUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalOrderField, sourceId, stageId, currencyId, responsibleUserId, timeoutMs);
    }
}