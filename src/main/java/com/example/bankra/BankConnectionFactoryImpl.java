package com.example.bankra;

import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;

import javax.naming.NamingException;
import javax.naming.Reference;

public class BankConnectionFactoryImpl implements BankConnectionFactory {

    private final BankManagedConnectionFactory mcf;
    private final ConnectionManager cm;
    private Reference reference;

    public BankConnectionFactoryImpl(BankManagedConnectionFactory mcf, ConnectionManager cm) {
        this.mcf = mcf;
        this.cm = cm;
    }

    @Override
    public BankConnection getConnection() throws ResourceException {
        Object handle = cm.allocateConnection(mcf, null);
        if (!(handle instanceof BankConnection)) {
            throw new ResourceException("Connection manager returned unexpected type: " + handle);
        }
        return (BankConnection) handle;
    }

    @Override
    public void setReference(Reference reference) {
        this.reference = reference;
    }

    @Override
    public Reference getReference() throws NamingException {
        if (reference == null) {
            throw new NamingException("Reference is not set");
        }
        return reference;
    }
}