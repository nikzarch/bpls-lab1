package com.example.bankra;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;

import javax.transaction.xa.XAResource;

public class BankResourceAdapter implements ResourceAdapter {

    @Override
    public void start(BootstrapContext ctx) throws ResourceAdapterInternalException {
    }

    @Override
    public void stop() {
    }

    @Override
    public void endpointActivation(MessageEndpointFactory factory, ActivationSpec spec) throws ResourceException {
        throw new ResourceException("Inbound is not supported");
    }

    @Override
    public void endpointDeactivation(MessageEndpointFactory factory, ActivationSpec spec) {
    }

    @Override
    public XAResource[] getXAResources(ActivationSpec[] specs) {
        return new XAResource[0];
    }
}