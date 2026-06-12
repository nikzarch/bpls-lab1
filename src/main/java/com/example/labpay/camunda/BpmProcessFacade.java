package com.example.labpay.camunda;

import com.example.labpay.domain.OrderStatus;
import com.example.labpay.domain.card.CardStatus;
import com.example.labpay.domain.transfer.TransferStatus;
import com.example.labpay.domain.transfer.TransferType;
import com.example.labpay.dto.request.*;
import com.example.labpay.dto.response.*;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.service.CardService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class BpmProcessFacade {

    private static final String CARD_BINDING_PROCESS = "card-binding-process";
    private static final String CARD_BINDING_CONFIRM_TASK = "card-binding-confirm-task";
    private static final String PAYMENT_CREATE_PROCESS = "payment-create-process";
    private static final String PAYMENT_PROCESS = "payment-process";
    private static final String TRANSFER_PROCESS = "transfer-process";
    private static final String TOP_UP_PROCESS = "wallet-top-up-process";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ProcessStartAuthorizer processStartAuthorizer;
    private final CardService cardService;

    public BpmProcessFacade(RuntimeService runtimeService,
                            TaskService taskService,
                            HistoryService historyService,
                            ProcessStartAuthorizer processStartAuthorizer,
                            CardService cardService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.processStartAuthorizer = processStartAuthorizer;
        this.cardService = cardService;
    }

    public ProcessLaunchResult start(String processKey, Map<String, Object> variables) {
        processStartAuthorizer.assertCanStart(processKey, currentUserGroups());

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(processKey, variables);
        Task task = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .orderByTaskCreateTime()
                .desc()
                .singleResult();

        return new ProcessLaunchResult(
                instance.getId(),
                instance.getProcessDefinitionId(),
                task != null ? task.getId() : null,
                task != null ? task.getName() : null,
                Optional.of(snapshotVariables(instance.getId()))
        );
    }

    public Set<String> currentUserGroups() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> groups = new LinkedHashSet<>();
        if (auth == null) {
            return groups;
        }
        for (var authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if (role.startsWith("ROLE_")) {
                role = role.substring("ROLE_".length());
            }
            groups.add(role);
        }
        return groups;
    }

    public BindCardResultResponse startCardBinding(String username, BindCardRequest request) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("username", username);
        vars.put("cardNumber", request.cardNumber());
        vars.put("holderName", request.holderName());
        vars.put("expiryDate", request.expiryDate());
        vars.put("cvv", request.cvv());

        ProcessLaunchResult result = start(CARD_BINDING_PROCESS, vars);
        return toBindCard(result.variables().orElse(Map.of()));
    }

    public CardResponse completeCardBinding3ds(String username, String sessionId, String code) {
        cardService.callBankConfirm3ds(sessionId, code);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(CARD_BINDING_PROCESS)
                .variableValueEquals("cardBindingSessionId", sessionId)
                .active()
                .singleResult();

        if (processInstance == null) {
            throw new NotFoundException("Card binding session not found");
        }

        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .taskDefinitionKey(CARD_BINDING_CONFIRM_TASK)
                .singleResult();

        if (task == null) {
            throw new NotFoundException("3DS confirmation task not found");
        }

        taskService.complete(task.getId(), Map.of("cardBindingConfirmationCode", code));
        return toCard(snapshotVariables(processInstance.getId()));
    }

    public PaymentOrderResponse startPaymentCreate(String username, CreatePaymentRequest request) {
        ProcessLaunchResult result = start(PAYMENT_CREATE_PROCESS, Map.of(
                "username", username,
                "widgetId", request.widgetId(),
                "productId", request.productId()
        ));
        return toPaymentOrder(result.variables().orElse(Map.of()));
    }

    public PaymentOrderResponse startPaymentProcess(String username, ProcessPaymentRequest request) {
        ProcessLaunchResult result = start(PAYMENT_PROCESS, Map.of(
                "username", username,
                "orderId", request.orderId(),
                "method", request.method().name(),
                "cardToken", request.cardToken() == null ? "" : request.cardToken()
        ));
        return toPaymentOrder(result.variables().orElse(Map.of()));
    }

    public TransferResponse startTransfer(String username, TransferRequest request) {
        ProcessLaunchResult result = start(TRANSFER_PROCESS, Map.of(
                "username", username,
                "recipientId", request.recipientId(),
                "amount", request.amount().toPlainString(),
                "source", request.source().name(),
                "type", request.type().name(),
                "cardToken", request.cardToken() == null ? "" : request.cardToken(),
                "idempotencyKey", request.idempotencyKey() == null ? "" : request.idempotencyKey()
        ));
        return toTransfer(result.variables().orElse(Map.of()));
    }

    public TopUpResultResponse startTopUp(String username, TopUpRequest request) {
        ProcessLaunchResult result = start(TOP_UP_PROCESS, Map.of(
                "username", username,
                "cardToken", request.cardToken(),
                "amount", request.amount().toPlainString()
        ));
        return toTopUp(result.variables().orElse(Map.of()));
    }

    private Map<String, Object> snapshotVariables(String processInstanceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<HistoricVariableInstance> historic = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();
        for (HistoricVariableInstance variable : historic) {
            out.put(variable.getVariableName(), variable.getValue());
        }
        if (!out.isEmpty()) {
            return out;
        }
        try {
            out.putAll(runtimeService.getVariables(processInstanceId));
        } catch (Exception ignored) {
        }
        return out;
    }

    private BindCardResultResponse toBindCard(Map<String, Object> vars) {
        boolean requires3ds = booleanValue(vars.get("requires3ds"));
        String sessionId = stringValue(vars.get("cardBindingSessionId"));
        String confirmationCode = stringValue(vars.get("cardBindingConfirmationCode"));
        CardResponse card = vars.containsKey("cardId") ? toCard(vars) : null;
        return new BindCardResultResponse(requires3ds, sessionId, confirmationCode, card);
    }

    private CardResponse toCard(Map<String, Object> vars) {
        if (!vars.containsKey("cardId")) {
            return null;
        }
        return new CardResponse(
                longValue(vars.get("cardId")),
                stringValue(vars.get("cardMaskedCardNumber")),
                stringValue(vars.get("cardHolderName")),
                vars.get("cardStatus") == null ? CardStatus.ACTIVE : CardStatus.valueOf(stringValue(vars.get("cardStatus"))),
                stringValue(vars.get("cardToken"))
        );
    }

    private PaymentOrderResponse toPaymentOrder(Map<String, Object> vars) {
        return new PaymentOrderResponse(
                longValue(vars.get("paymentOrderId")),
                stringValue(vars.get("paymentExternalOrderId")),
                vars.get("paymentStatus") == null ? OrderStatus.CREATED : OrderStatus.valueOf(stringValue(vars.get("paymentStatus"))),
                decimalValue(vars.get("paymentAmount")),
                stringValue(vars.get("paymentProductTitle")),
                instantValue(vars.get("paymentCreatedAt")),
                instantValue(vars.get("paymentPaidAt"))
        );
    }

    private TransferResponse toTransfer(Map<String, Object> vars) {
        return new TransferResponse(
                longValue(vars.get("transferId")),
                longValue(vars.get("senderId")),
                longValue(vars.get("recipientId")),
                decimalValue(vars.get("amount")),
                vars.get("type") == null ? TransferType.USER_TO_USER : TransferType.valueOf(stringValue(vars.get("type"))),
                vars.get("transferStatus") == null ? TransferStatus.SUCCESS : TransferStatus.valueOf(stringValue(vars.get("transferStatus"))),
                instantValue(vars.get("transferCreatedAt"))
        );
    }

    private TopUpResultResponse toTopUp(Map<String, Object> vars) {
        return new TopUpResultResponse(
                stringValue(vars.get("topUpState")),
                stringValue(vars.get("topUpCorrelationId")),
                longValue(vars.get("walletId")),
                decimalValue(vars.get("balance")),
                stringValue(vars.get("topUpMessage"))
        );
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(String.valueOf(value));
    }

    private Instant instantValue(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        return Instant.parse(String.valueOf(value));
    }

    private boolean booleanValue(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public record ProcessLaunchResult(
            String processInstanceId,
            String processDefinitionId,
            String taskId,
            String taskName,
            Optional<Map<String, Object>> variables
    ) {}
}