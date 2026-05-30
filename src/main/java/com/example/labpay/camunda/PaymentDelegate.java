package com.example.labpay.camunda;

import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.request.ProcessPaymentRequest;
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

        long widgetId = longValue(execution, "widgetId");
        long productId = longValue(execution, "productId");
        ProcessPaymentRequest.PaymentMethod method =
                enumValue(execution, "method", ProcessPaymentRequest.PaymentMethod.class);
        String cardToken = value(execution, "cardToken");

        PaymentOrderResponse created = paymentService.createOrder(
                username,
                new CreatePaymentRequest(widgetId, productId)
        );

        PaymentOrderResponse processed = paymentService.processPayment(
                username,
                new ProcessPaymentRequest(created.id(), method, cardToken)
        );

        execution.setVariable("paymentOrderId", processed.id());
        execution.setVariable("paymentExternalOrderId", processed.externalOrderId());
        execution.setVariable("paymentStatus", processed.status().name());
        execution.setVariable("paymentAmount", processed.amount().toPlainString());
        execution.setVariable("paymentProductTitle", processed.productTitle());
        execution.setVariable("paymentCreatedAt", processed.createdAt() == null ? null : processed.createdAt().toString());
        execution.setVariable("paymentPaidAt", processed.paidAt() == null ? null : processed.paidAt().toString());
    }

    private String value(DelegateExecution execution, String key) {
        Object v = execution.getVariable(key);
        return v == null ? null : String.valueOf(v);
    }

    private long longValue(DelegateExecution execution, String key) {
        Object v = execution.getVariable(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }

    private <E extends Enum<E>> E enumValue(DelegateExecution execution, String key, Class<E> type) {
        return Enum.valueOf(type, String.valueOf(execution.getVariable(key)));
    }
}