package com.example.labpay.integration.bitrix.ra;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;

@jakarta.resource.spi.Connector(
        displayName = "Bitrix CRM Resource Adapter",
        vendorName = "LabPay",
        version = "1.0"
)
public class BitrixResourceAdapter implements ResourceAdapter {

    @Override
    public void start(BootstrapContext ctx) {
    }

    @Override
    public void stop() {
    }

    @Override
    public void endpointActivation(MessageEndpointFactory endpointFactory, ActivationSpec spec) throws ResourceException {
    }

    @Override
    public void endpointDeactivation(MessageEndpointFactory endpointFactory, ActivationSpec spec) {
    }

    @Override
    public javax.transaction.xa.XAResource[] getXAResources(ActivationSpec[] specs) {
        return new XAResource[0];
    }

}