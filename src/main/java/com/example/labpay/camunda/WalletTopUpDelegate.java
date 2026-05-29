package com.example.labpay.camunda;

import com.example.labpay.dto.request.TopUpRequest;
import com.example.labpay.dto.response.TopUpResultResponse;
import com.example.labpay.service.WalletService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("walletTopUpDelegate")
public class WalletTopUpDelegate extends AbstractCamundaDelegateSupport implements JavaDelegate {

    private final WalletService walletService;

    public WalletTopUpDelegate(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String username = string(execution, "username");
        TopUpRequest request = new TopUpRequest(
                string(execution, "cardToken"),
                decimal(execution, "amount")
        );

        TopUpResultResponse result = walletService.topUp(username, request);
        execution.setVariable("topUpState", result.state());
        execution.setVariable("topUpCorrelationId", result.correlationId());
        execution.setVariable("walletId", result.walletId());
        execution.setVariable("balance", result.balance() == null ? null : result.balance().toPlainString());
        execution.setVariable("topUpMessage", result.message());
    }
}
