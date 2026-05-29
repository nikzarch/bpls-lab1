package com.example.labpay.camunda;

import com.example.labpay.dto.request.ProcessPaymentRequest;
import com.example.labpay.dto.response.PaymentOrderResponse;
import com.example.labpay.service.PaymentService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("paymentProcessDelegate")
public class PaymentProcessDelegate extends AbstractCamundaDelegateSupport implements JavaDelegate {

    private final PaymentService paymentService;

    public PaymentProcessDelegate(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String username = string(execution, "username");
        ProcessPaymentRequest request = new ProcessPaymentRequest(
                longValue(execution, "orderId"),
                enumValue(execution, "method", ProcessPaymentRequest.PaymentMethod.class),
                string(execution, "cardToken")
        );

        PaymentOrderResponse order = paymentService.processPayment(username, request);
        execution.setVariable("paymentOrderId", order.id());
        execution.setVariable("paymentExternalOrderId", order.externalOrderId());
        execution.setVariable("paymentStatus", order.status().name());
        execution.setVariable("paymentAmount", order.amount().toPlainString());
        execution.setVariable("paymentProductTitle", order.productTitle());
        execution.setVariable("paymentCreatedAt", order.createdAt() == null ? null : order.createdAt().toString());
        execution.setVariable("paymentPaidAt", order.paidAt() == null ? null : order.paidAt().toString());
    }
}
