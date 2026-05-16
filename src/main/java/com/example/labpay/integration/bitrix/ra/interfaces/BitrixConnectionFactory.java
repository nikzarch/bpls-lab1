package com.example.labpay.integration.bitrix.ra.interfaces;

import jakarta.resource.ResourceException;

public interface BitrixConnectionFactory {
    BitrixConnection getConnection() throws ResourceException;
}