package com.example.labpay.integration.bitrix.ra;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;

import javax.security.auth.Subject;

public class BitrixSimpleConnectionManager implements ConnectionManager {

    @Override
    public Object allocateConnection(ManagedConnectionFactory mcf, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        ManagedConnection mc = mcf.createManagedConnection((Subject) null, cxRequestInfo);
        return mc.getConnection(null, cxRequestInfo);
    }
}