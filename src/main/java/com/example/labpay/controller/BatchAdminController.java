package com.example.labpay.controller;

import com.example.labpay.batch.BatchJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/batch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BatchAdminController {

    private final BatchJobRunner batchJobRunner;

    @GetMapping("/jobs")
    public Map<String, Set<String>> jobs() {
        return Map.of("jobs", batchJobRunner.jobNames());
    }

    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<BatchJobResponse> run(@PathVariable String jobName) throws Exception {
        return started(batchJobRunner.run(jobName, "manual"));
    }

    @PostMapping("/bank-reconciliation/run")
    public ResponseEntity<BatchJobResponse> runBankReconciliation() throws Exception {
        return started(batchJobRunner.run("bankReconciliationJob", "manual"));
    }

    @PostMapping("/card-session-cleanup/run")
    public ResponseEntity<BatchJobResponse> runCardSessionCleanup() throws Exception {
        return started(batchJobRunner.run("cardSessionCleanupJob", "manual"));
    }

    @PostMapping("/hold-expiration/run")
    public ResponseEntity<BatchJobResponse> runHoldExpiration() throws Exception {
        return started(batchJobRunner.run("holdExpirationJob", "manual"));
    }

    @PostMapping("/stuck-transfer/run")
    public ResponseEntity<BatchJobResponse> runStuckTransfer() throws Exception {
        return started(batchJobRunner.run("stuckTransferJob", "manual"));
    }

    private ResponseEntity<BatchJobResponse> started(JobExecution execution) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(BatchJobResponse.from(execution));
    }

    public record BatchJobResponse(
            Long executionId,
            String jobName,
            String status,
            String exitStatus,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Map<String, Object> parameters
    ) {
        static BatchJobResponse from(JobExecution e) {
            Map<String, Object> params = e.getJobParameters()
                    .getParameters()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            x -> x.getValue().getValue()
                    ));

            return new BatchJobResponse(
                    e.getId(),
                    e.getJobInstance().getJobName(),
                    e.getStatus().name(),
                    e.getExitStatus().getExitCode(),
                    e.getStartTime(),
                    e.getEndTime(),
                    params
            );
        }
    }
}