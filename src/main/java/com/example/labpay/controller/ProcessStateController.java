package com.example.labpay.controller;

import com.example.labpay.camunda.state.ProcessState;
import com.example.labpay.camunda.state.ProcessStateRepository;
import com.example.labpay.camunda.state.ProcessStateStatus;
import com.example.labpay.dto.response.ListResponse;
import com.example.labpay.exception.NotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/process-states")
@RequiredArgsConstructor
@Tag(name = "ProcessStates", description = "Состояния бизнес-процессов Camunda")
public class ProcessStateController {

    private final ProcessStateRepository repository;

    public record ProcessStateResponse(
            Long id,
            String processInstanceId,
            String processDefinitionId,
            String currentActivityId,
            String currentActivityName,
            ProcessStateStatus status,
            String ownerUsername,
            Instant startedAt,
            Instant updatedAt,
            Instant endedAt,
            String errorMessage
    ) {
        static ProcessStateResponse of(ProcessState s) {
            return new ProcessStateResponse(
                    s.getId(),
                    s.getProcessInstanceId(),
                    s.getProcessDefinitionId(),
                    s.getCurrentActivityId(),
                    s.getCurrentActivityName(),
                    s.getStatus(),
                    s.getOwnerUsername(),
                    s.getStartedAt(),
                    s.getUpdatedAt(),
                    s.getEndedAt(),
                    s.getErrorMessage()
            );
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ListResponse<ProcessStateResponse> list(Authentication auth) {
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        List<ProcessState> states = admin
                ? repository.findAll()
                : repository.findByOwnerUsername(auth.getName());

        return new ListResponse<>(states.stream().map(ProcessStateResponse::of).toList());
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ListResponse<ProcessStateResponse> byStatus(@PathVariable ProcessStateStatus status) {
        return new ListResponse<>(
                repository.findByStatus(status).stream().map(ProcessStateResponse::of).toList()
        );
    }

    @GetMapping("/{processInstanceId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ProcessStateResponse get(Authentication auth, @PathVariable String processInstanceId) {
        ProcessState state = repository.findByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new NotFoundException("Process state not found"));

        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && !auth.getName().equals(state.getOwnerUsername())) {
            throw new NotFoundException("Process state not found");
        }
        return ProcessStateResponse.of(state);
    }
}