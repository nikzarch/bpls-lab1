package com.example.labpay.camunda;

import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.request.StartPaymentProcessRequest;
import com.example.labpay.dto.response.PaymentOrderResponse;
import com.example.labpay.service.PaymentService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component("paymentDelegate")
public class PaymentDelegate {

    private final PaymentService paymentService;

    public PaymentDelegate(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void createAndProcess(DelegateExecution execution) {
        String username = value(execution, "username");
        StartPaymentProcessRequest start = new StartPaymentProcessRequest(
                longValue(execution, "widgetId"),
                longValue(execution, "productId"),
                enumValue(execution, "method", ProcessPaymentRequest.PaymentMethod.class),
                value(execution, "cardToken")
        );

        PaymentOrderResponse created = paymentService.createOrder(
                username,
                new CreatePaymentRequest(start.widgetId(), start.productId())
        );

        PaymentOrderResponse processed = paymentService.processPayment(
                username,
                new ProcessPaymentRequest(created.id(), start.method(), start.cardToken())
        );

        execution.setVariable("paymentOrderId", processed.id());
        execution.setVariable("paymentExternalOrderId", processed.externalOrderId());
        execution.setVariable("paymentStatus", processed.status().name());
        execution.setVariable("paymentAmount", processed.amount().toPlainString());
        execution.setVariable("paymentProductTitle", processed.productTitle());
    }

    private String value(DelegateExecution execution, String key) {
        Object v = execution.getVariable(key);
        return v == null ? null : String.valueOf(v);
    }

    private long longValue(DelegateExecution execution, String key) {
        Object v = execution.getVariable(key);
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private <E extends Enum<E>> E enumValue(DelegateExecution execution, String key, Class<E> type) {
        return Enum.valueOf(type, String.valueOf(execution.getVariable(key)));
    }
}
