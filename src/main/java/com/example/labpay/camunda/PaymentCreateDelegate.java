package com.example.labpay.camunda;

import com.example.labpay.dto.request.CreatePaymentRequest;
import com.example.labpay.dto.response.PaymentOrderResponse;
import com.example.labpay.service.PaymentService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("paymentCreateDelegate")
public class PaymentCreateDelegate extends AbstractCamundaDelegateSupport implements JavaDelegate {

    private final PaymentService paymentService;

    public PaymentCreateDelegate(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String username = string(execution, "username");
        CreatePaymentRequest request = new CreatePaymentRequest(
                longValue(execution, "widgetId"),
                longValue(execution, "productId")
        );

        PaymentOrderResponse order = paymentService.createOrder(username, request);
        execution.setVariable("paymentOrderId", order.id());
        execution.setVariable("paymentExternalOrderId", order.externalOrderId());
        execution.setVariable("paymentStatus", order.status().name());
        execution.setVariable("paymentAmount", order.amount().toPlainString());
        execution.setVariable("paymentProductTitle", order.productTitle());
        execution.setVariable("paymentCreatedAt", order.createdAt() == null ? null : order.createdAt().toString());
        execution.setVariable("paymentPaidAt", order.paidAt() == null ? null : order.paidAt().toString());
    }
}
