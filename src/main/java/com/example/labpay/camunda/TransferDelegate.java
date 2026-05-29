package com.example.labpay.camunda;

import com.example.labpay.dto.request.TransferRequest;
import com.example.labpay.dto.response.TransferResponse;
import com.example.labpay.service.TransferService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("transferDelegate")
public class TransferDelegate extends AbstractCamundaDelegateSupport implements JavaDelegate {

    private final TransferService transferService;

    public TransferDelegate(TransferService transferService) {
        this.transferService = transferService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String username = string(execution, "username");
        TransferRequest request = new TransferRequest(
                longValue(execution, "recipientId"),
                decimal(execution, "amount"),
                enumValue(execution, "source", TransferRequest.TransferSource.class),
                enumValue(execution, "type", com.example.labpay.domain.transfer.TransferType.class),
                string(execution, "cardToken"),
                string(execution, "idempotencyKey")
        );

        TransferResponse response = transferService.createTransfer(username, request);
        execution.setVariable("transferId", response.id());
        execution.setVariable("senderId", response.senderId());
        execution.setVariable("recipientId", response.recipientId());
        execution.setVariable("amount", response.amount().toPlainString());
        execution.setVariable("type", response.type().name());
        execution.setVariable("transferStatus", response.status().name());
        execution.setVariable("transferCreatedAt", response.createdAt() == null ? null : response.createdAt().toString());
    }
}
