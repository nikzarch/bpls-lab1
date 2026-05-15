package com.example.labpay.integration.bitrix.ra;

import jakarta.resource.ResourceException;

public interface BitrixConnectionFactory {
    BitrixConnection getConnection() throws ResourceException;
}