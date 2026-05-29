package com.example.labpay.camunda;

import com.example.labpay.dto.request.BindCardRequest;
import com.example.labpay.dto.request.Confirm3dsRequest;
import com.example.labpay.dto.response.BindCardResultResponse;
import com.example.labpay.dto.response.CardResponse;
import com.example.labpay.service.CardService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component("cardBindingDelegate")
public class CardBindingDelegate {

    private final CardService cardService;

    public CardBindingDelegate(CardService cardService) {
        this.cardService = cardService;
    }

    public void bind(DelegateExecution execution) {
        String username = value(execution, "username");
        BindCardRequest request = new BindCardRequest(
                value(execution, "cardNumber"),
                value(execution, "holderName"),
                value(execution, "expiryDate"),
                value(execution, "cvv")
        );

        BindCardResultResponse result = cardService.bindCard(username, request);

        execution.setVariable("requires3ds", result.requires3ds());
        execution.setVariable("cardBindingSessionId", result.sessionId());
        execution.setVariable("cardBindingConfirmationCode", result.confirmationCode());

        if (result.card() != null) {
            execution.setVariable("cardId", result.card().id());
            execution.setVariable("cardToken", result.card().token());
            execution.setVariable("cardMaskedCardNumber", result.card().maskedCardNumber());
            execution.setVariable("cardHolderName", result.card().holderName());
            execution.setVariable("cardStatus", result.card().status().name());
        }
    }

    public void confirm3ds(DelegateExecution execution) {
        String username = value(execution, "username");
        Confirm3dsRequest request = new Confirm3dsRequest(
                value(execution, "cardBindingSessionId"),
                value(execution, "cardBindingConfirmationCode")
        );

        CardResponse card = cardService.confirm3ds(username, request);
        execution.setVariable("cardId", card.id());
        execution.setVariable("cardToken", card.token());
        execution.setVariable("cardMaskedCardNumber", card.maskedCardNumber());
        execution.setVariable("cardHolderName", card.holderName());
        execution.setVariable("cardStatus", card.status().name());
    }

    public void fail(DelegateExecution execution) {
        execution.setVariable("bindingError", "Card binding failed");
    }

    @SuppressWarnings("unchecked")
    private String value(DelegateExecution execution, String key) {
        Object v = execution.getVariable(key);
        return v == null ? null : String.valueOf(v);
    }
}
