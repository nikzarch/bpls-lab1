package com.example.labpay.camunda;

import com.example.labpay.dto.response.CardResponse;
import com.example.labpay.service.CardService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("cardBindingConfirmDelegate")
public class CardBindingConfirmDelegate extends AbstractCamundaDelegateSupport implements JavaDelegate {

    private final CardService cardService;

    public CardBindingConfirmDelegate(CardService cardService) {
        this.cardService = cardService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String username = string(execution, "username");
        String sessionId = string(execution, "cardBindingSessionId");

        CardResponse card = cardService.persistConfirmedCard(username, sessionId);
        execution.setVariable("cardId", card.id());
        execution.setVariable("cardToken", card.token());
        execution.setVariable("cardMaskedCardNumber", card.maskedCardNumber());
        execution.setVariable("cardHolderName", card.holderName());
        execution.setVariable("cardStatus", card.status().name());
    }
}