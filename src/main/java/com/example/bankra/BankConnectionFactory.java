package com.example.bankra;

import jakarta.resource.Referenceable;
import jakarta.resource.ResourceException;
import java.io.Serializable;

public interface BankConnectionFactory extends Serializable, Referenceable {
    BankConnection getConnection() throws ResourceException;
}