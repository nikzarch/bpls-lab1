package com.example.labpay.integration.bitrix.ra;

import com.example.labpay.integration.bitrix.ra.dto.BitrixConnectionRequestInfo;
import com.example.labpay.integration.bitrix.ra.interfaces.BitrixConnection;
import com.example.labpay.integration.bitrix.ra.interfaces.BitrixConnectionFactory;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionManager;

public class BitrixConnectionFactoryImpl implements BitrixConnectionFactory {

    private final BitrixManagedConnectionFactory mcf;
    private final ConnectionManager connectionManager;

    public BitrixConnectionFactoryImpl(BitrixManagedConnectionFactory mcf, ConnectionManager connectionManager) {
        this.mcf = mcf;
        this.connectionManager = connectionManager;
    }

    @Override
    public BitrixConnection getConnection() throws ResourceException {
        return (BitrixConnection) connectionManager.allocateConnection(mcf, new BitrixConnectionRequestInfo());
    }
}