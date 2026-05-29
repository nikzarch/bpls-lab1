package com.example.labpay.camunda;

import com.example.labpay.dto.request.BindCardRequest;
import com.example.labpay.dto.response.BindCardResultResponse;
import com.example.labpay.dto.response.CardResponse;
import com.example.labpay.service.CardService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("cardBindingStartDelegate")
public class CardBindingStartDelegate extends AbstractCamundaDelegateSupport implements JavaDelegate {

    private final CardService cardService;

    public CardBindingStartDelegate(CardService cardService) {
        this.cardService = cardService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String username = string(execution, "username");
        BindCardRequest request = new BindCardRequest(
                string(execution, "cardNumber"),
                string(execution, "holderName"),
                string(execution, "expiryDate"),
                string(execution, "cvv")
        );

        BindCardResultResponse result = cardService.bindCard(username, request);

        execution.setVariable("requires3ds", result.requires3ds());
        execution.setVariable("cardBindingSessionId", result.sessionId());
        execution.setVariable("cardBindingConfirmationCode", result.confirmationCode());

        if (result.card() != null) {
            CardResponse card = result.card();
            execution.setVariable("cardId", card.id());
            execution.setVariable("cardToken", card.token());
            execution.setVariable("cardMaskedCardNumber", card.maskedCardNumber());
            execution.setVariable("cardHolderName", card.holderName());
            execution.setVariable("cardStatus", card.status().name());
        }
    }
}
