package com.example.labpay.controller;

import com.example.labpay.camunda.BpmProcessFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/batch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BatchAdminController {

    private final BpmProcessFacade bpmProcessFacade;

    @GetMapping("/jobs")
    public Map<String, Set<String>> jobs() {
        return Map.of("jobs", Set.of(
                "bank-reconciliation",
                "hold-expiration",
                "card-session-cleanup",
                "stuck-transfer"
        ));
    }

    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<BpmProcessFacade.ProcessLaunchResult> run(@PathVariable String jobName) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(start(jobName));
    }

    @PostMapping("/bank-reconciliation/run")
    public ResponseEntity<BpmProcessFacade.ProcessLaunchResult> runBankReconciliation() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(start("bank-reconciliation"));
    }

    @PostMapping("/card-session-cleanup/run")
    public ResponseEntity<BpmProcessFacade.ProcessLaunchResult> runCardSessionCleanup() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(start("card-session-cleanup"));
    }

    @PostMapping("/hold-expiration/run")
    public ResponseEntity<BpmProcessFacade.ProcessLaunchResult> runHoldExpiration() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(start("hold-expiration"));
    }

    @PostMapping("/stuck-transfer/run")
    public ResponseEntity<BpmProcessFacade.ProcessLaunchResult> runStuckTransfer() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(start("stuck-transfer"));
    }

    private BpmProcessFacade.ProcessLaunchResult start(String jobName) {
        return switch (jobName) {
            case "bank-reconciliation" -> bpmProcessFacade.start("maintenance-bank-reconciliation", Map.of("trigger", "manual"));
            case "hold-expiration" -> bpmProcessFacade.start("maintenance-hold-expiration", Map.of("trigger", "manual"));
            case "card-session-cleanup" -> bpmProcessFacade.start("maintenance-card-session-cleanup", Map.of("trigger", "manual"));
            case "stuck-transfer" -> bpmProcessFacade.start("maintenance-stuck-transfer", Map.of("trigger", "manual"));
            default -> throw new IllegalArgumentException("Unknown maintenance job: " + jobName);
        };
    }
}
