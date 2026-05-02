package com.example.bankra;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionEvent;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.LocalTransaction;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionMetaData;
import javax.security.auth.Subject;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.transaction.xa.XAResource;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BankManagedConnection implements ManagedConnection {

    private static final String REQUEST_QUEUE = "/queues/bank.requests";
    private static final String RESPONSE_QUEUE = "/queues/bank.responses";

    private final BankManagedConnectionFactory mcf;
    private final ConnectionFactory amqpConnectionFactory;
    private final long timeoutMs;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ConnectionEventListener> listeners = new ArrayList<>();
    private final List<BankConnectionImpl> handles = new ArrayList<>();
    private PrintWriter logWriter;
    private boolean destroyed;

    public BankManagedConnection(BankManagedConnectionFactory mcf,
                                 ConnectionFactory amqpConnectionFactory,
                                 long timeoutMs) {
        this.mcf = mcf;
        this.amqpConnectionFactory = amqpConnectionFactory;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public synchronized Object getConnection(Subject subject, ConnectionRequestInfo info) throws ResourceException {
        if (destroyed) {
            throw new ResourceException("Managed connection is destroyed");
        }
        BankConnectionImpl handle = new BankConnectionImpl(this);
        handles.add(handle);
        return handle;
    }

    void handleClosed(BankConnectionImpl handle) {
        synchronized (this) {
            handles.remove(handle);
            handle.invalidate();
        }
        ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
        event.setConnectionHandle(handle);
        for (ConnectionEventListener l : listeners) {
            l.connectionClosed(event);
        }
    }

    @Override
    public synchronized void destroy() {
        destroyed = true;
        for (BankConnectionImpl h : handles) {
            h.invalidate();
        }
        handles.clear();
    }

    @Override
    public synchronized void cleanup() {
        for (BankConnectionImpl h : handles) {
            h.invalidate();
        }
        handles.clear();
    }

    @Override
    public void associateConnection(Object connection) throws ResourceException {
        if (!(connection instanceof BankConnectionImpl handle)) {
            throw new ResourceException("Unexpected connection type");
        }
        synchronized (this) {
            handles.add(handle);
        }
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        throw new NotSupportedException("XA transactions are not supported by bank adapter");
    }

    @Override
    public LocalTransaction getLocalTransaction() throws ResourceException {
        throw new NotSupportedException("Local transactions are not supported by bank adapter");
    }

    @Override
    public ManagedConnectionMetaData getMetaData() {
        return new ManagedConnectionMetaData() {
            @Override public String getEISProductName() { return "Bank Emulator"; }
            @Override public String getEISProductVersion() { return "1.0"; }
            @Override public int getMaxConnections() { return 0; }
            @Override public String getUserName() { return null; }
        };
    }

    @Override
    public void setLogWriter(PrintWriter out) { this.logWriter = out; }

    @Override
    public PrintWriter getLogWriter() { return logWriter; }

    void validate(String cardNumber) {
        sendCommand("VALIDATE", Map.of("cardNumber", cardNumber));
    }

    String initiateBind(String cardNumber, String cvv, String expiry) {
        return sendCommand("INIT_BIND",
                Map.of("cardNumber", cardNumber, "cvv", cvv, "expiry", expiry));
    }

    void confirm3ds(String sessionId, String code) {
        sendCommand("CONFIRM_3DS", Map.of("sessionId", sessionId, "code", code));
    }

    String initiateCharge(String cardNumber, double amount) {
        return sendCommand("INIT_CHARGE", Map.of("cardNumber", cardNumber, "amount", amount));
    }

    void completeCharge(String sessionId, double amount) {
        sendCommand("COMPLETE_CHARGE", Map.of("sessionId", sessionId, "amount", amount));
    }

    void directCharge(String cardNumber, double amount) {
        sendCommand("DIRECT_CHARGE", Map.of("cardNumber", cardNumber, "amount", amount));
    }

    private String sendCommand(String operation, Map<String, Object> payload) {
        String corrId = UUID.randomUUID().toString();

        try (JMSContext ctx = amqpConnectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue requestQueue = ctx.createQueue(REQUEST_QUEUE);
            Queue responseQueue = ctx.createQueue(RESPONSE_QUEUE);

            Map<String, Object> cmd = new HashMap<>();
            cmd.put("correlationId", corrId);
            cmd.put("operation", operation);
            cmd.put("payload", mapper.writeValueAsString(payload));
            cmd.put("replyQueue", RESPONSE_QUEUE);
            String cmdJson = mapper.writeValueAsString(cmd);

            try (JMSConsumer consumer = ctx.createConsumer(
                    responseQueue, "JMSCorrelationID = '" + corrId + "'")) {

                ctx.createProducer()
                        .setJMSCorrelationID(corrId)
                        .setJMSReplyTo(responseQueue)
                        .send(requestQueue, cmdJson);

                Message replyMessage = consumer.receive(timeoutMs);
                if (replyMessage == null) {
                    throw new BankAdapterException("Bank reply timeout for op " + operation);
                }

                String replyJson = readJmsBodyAsString(replyMessage);

                @SuppressWarnings("unchecked")
                Map<String, Object> reply = mapper.readValue(replyJson, Map.class);
                Boolean ok = (Boolean) reply.get("ok");
                if (!Boolean.TRUE.equals(ok)) {
                    Object err = reply.get("error");
                    throw new BankAdapterException(err == null ? "bank error" : err.toString());
                }
                Object p = reply.get("payload");
                return p == null ? null : p.toString();
            }
        } catch (BankAdapterException e) {
            throw e;
        } catch (Exception e) {
            throw new BankAdapterException("Bank adapter failure: " + e.getMessage());
        }
    }

    private String readJmsBodyAsString(Message message) throws Exception {
        if (message instanceof TextMessage textMessage) {
            return textMessage.getText();
        }

        if (message instanceof BytesMessage bytesMessage) {
            bytesMessage.reset();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            int read;
            while ((read = bytesMessage.readBytes(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            return out.toString(StandardCharsets.UTF_8);
        }

        if (message.isBodyAssignableTo(String.class)) {
            return message.getBody(String.class);
        }

        if (message.isBodyAssignableTo(byte[].class)) {
            return new String(message.getBody(byte[].class), StandardCharsets.UTF_8);
        }

        throw new BankAdapterException(
                "Unsupported bank reply JMS message type: " + message.getClass().getName()
        );
    }
}