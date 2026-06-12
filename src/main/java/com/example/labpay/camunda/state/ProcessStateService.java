package com.example.labpay.camunda.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProcessStateService {

    private final ProcessStateRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProcessState onExecutionEvent(DelegateExecution execution) {
        String activityId = execution.getCurrentActivityId();
        String eventName = execution.getEventName();

        ProcessStateStatus status = classify(activityId, eventName);
        String errorMessage = status == ProcessStateStatus.FAILED ? "Camunda execution failed" : null;

        return upsertFromExecution(
                execution,
                status,
                activityId,
                execution.getCurrentActivityName(),
                errorMessage
        );
    }

    @Transactional
    public ProcessState onUserTaskCreate(DelegateTask task) {
        DelegateExecution execution = task.getExecution();
        return upsertFromExecution(
                execution,
                ProcessStateStatus.WAITING_USER_TASK,
                task.getTaskDefinitionKey(),
                task.getName(),
                null
        );
    }

    @Transactional
    public ProcessState onUserTaskComplete(DelegateTask task) {
        DelegateExecution execution = task.getExecution();
        return upsertFromExecution(
                execution,
                ProcessStateStatus.ACTIVE,
                task.getTaskDefinitionKey(),
                task.getName(),
                null
        );
    }

    @Transactional
    public ProcessState onProcessFailed(DelegateExecution execution, Throwable error) {
        return upsertFromExecution(
                execution,
                ProcessStateStatus.FAILED,
                execution.getCurrentActivityId(),
                execution.getCurrentActivityName(),
                error == null ? null : error.getMessage()
        );
    }

    private ProcessStateStatus classify(String activityId, String eventName) {
        String normalized = activityId == null ? "" : activityId.toLowerCase();

        if (normalized.contains("timeout") || normalized.contains("failed") || normalized.endsWith("-end") || normalized.endsWith("_end")) {
            return ProcessStateStatus.COMPLETED;
        }

        if (normalized.contains("start")) {
            return ProcessStateStatus.STARTED;
        }

        // For service-task listeners and any other execution listeners,
        // keep the process alive in ACTIVE state.
        if (normalized.contains("task") || normalized.contains("service")) {
            return ProcessStateStatus.ACTIVE;
        }

        return ProcessStateStatus.ACTIVE;
    }

    private ProcessState upsertFromExecution(
            DelegateExecution execution,
            ProcessStateStatus status,
            String activityId,
            String activityName,
            String errorMessage
    ) {
        String processInstanceId = execution.getProcessInstanceId();
        Instant now = Instant.now();

        ProcessState state = repository.findByProcessInstanceId(processInstanceId)
                .orElseGet(() -> ProcessState.builder()
                        .processInstanceId(processInstanceId)
                        .startedAt(now)
                        .build());

        state.setProcessDefinitionId(optionalString(execution.getProcessDefinitionId(), state.getProcessDefinitionId()));
        state.setBusinessKey(optionalString(execution.getBusinessKey(), state.getBusinessKey()));
        state.setCurrentActivityId(activityId);
        state.setCurrentActivityName(activityName);
        state.setStatus(status);
        state.setVariablesJson(serializeVariables(execution.getVariables()));
        state.setOwnerUsername(resolveOwner(execution, state.getOwnerUsername()));
        state.setUpdatedAt(now);
        state.setEndedAt(status == ProcessStateStatus.COMPLETED || status == ProcessStateStatus.FAILED ? now : null);
        state.setErrorMessage(errorMessage);

        return repository.save(state);
    }

    private String resolveOwner(DelegateExecution execution, String fallback) {
        Object username = execution.getVariable("username");
        if (username != null && !String.valueOf(username).isBlank()) {
            return String.valueOf(username);
        }
        return fallback;
    }

    private String optionalString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String serializeVariables(Map<String, Object> variables) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                Object value = entry.getValue();
                safe.put(entry.getKey(), value == null ? null : value);
            }
        }

        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception ex) {
            Map<String, String> fallback = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : safe.entrySet()) {
                fallback.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            try {
                return objectMapper.writeValueAsString(fallback);
            } catch (Exception ignored) {
                return "{}";
            }
        }
    }

    public Optional<ProcessState> findByProcessInstanceId(String processInstanceId) {
        return repository.findByProcessInstanceId(processInstanceId);
    }
}
