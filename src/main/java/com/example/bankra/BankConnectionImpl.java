package com.example.bankra;

public class BankConnectionImpl implements BankConnection {

    private BankManagedConnection mc;

    public BankConnectionImpl(BankManagedConnection mc) {
        this.mc = mc;
    }

    void invalidate() {
        this.mc = null;
    }

    private BankManagedConnection require() {
        if (mc == null) {
            throw new IllegalStateException("Connection is closed");
        }
        return mc;
    }

    @Override
    public void validate(String cardNumber) {
        require().validate(cardNumber);
    }

    @Override
    public String initiateBind(String cardNumber, String cvv, String expiry) {
        return require().initiateBind(cardNumber, cvv, expiry);
    }

    @Override
    public void confirm3ds(String sessionId, String code) {
        require().confirm3ds(sessionId, code);
    }

    @Override
    public String initiateCharge(String cardNumber, double amount) {
        return require().initiateCharge(cardNumber, amount);
    }

    @Override
    public void completeCharge(String sessionId, double amount) {
        require().completeCharge(sessionId, amount);
    }

    @Override
    public void directCharge(String cardNumber, double amount) {
        require().directCharge(cardNumber, amount);
    }

    @Override
    public void close() {
        if (mc != null) {
            mc.handleClosed(this);
            mc = null;
        }
    }
}