package com.example.labpay.integration.bitrix.ra;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.LocalTransaction;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionMetaData;
import lombok.RequiredArgsConstructor;

import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class BitrixManagedConnection implements ManagedConnection {

    private final BitrixApiClient apiClient;
    private final List<ConnectionEventListener> listeners = new ArrayList<>();

    @Override
    public Object getConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) {
        return new BitrixConnectionImpl(this, apiClient);
    }

    @Override
    public void destroy() {
        listeners.clear();
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void associateConnection(Object connection) {
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        return null;
    }

    @Override
    public LocalTransaction getLocalTransaction() {
        return null;
    }

    @Override
    public ManagedConnectionMetaData getMetaData() {
        return new ManagedConnectionMetaData() {
            @Override
            public String getEISProductName() {
                return "Bitrix24";
            }

            @Override
            public String getEISProductVersion() {
                return "1.0";
            }

            @Override
            public int getMaxConnections() {
                return 1;
            }

            @Override
            public String getUserName() {
                return "bitrix";
            }
        };
    }

    @Override
    public void setLogWriter(PrintWriter out) throws ResourceException {
        this.setLogWriter(out);
    }
    @Override
    public PrintWriter getLogWriter() throws ResourceException {
        return new PrintWriter(System.out);
    }

    BitrixApiClient apiClient() {
        return apiClient;
    }
}
