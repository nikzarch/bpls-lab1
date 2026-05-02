package com.example.bankra;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;

public class DefaultConnectionManager implements ConnectionManager {

    @Override
    public Object allocateConnection(ManagedConnectionFactory mcf, ConnectionRequestInfo info) throws ResourceException {
        ManagedConnection mc = mcf.createManagedConnection(null, info);
        return mc.getConnection(null, info);
    }
}