package com.example.labpay.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchJobStartService {

    private final BatchJobRunner batchJobRunner;

    @Async("camundaBatchExecutor")
    public CompletableFuture<Void> startAsync(String jobName, String trigger) {
        try {
            var execution = batchJobRunner.run(jobName, trigger);
            log.info("Batch job {} accepted from {}, executionId={}, status={}",
                    jobName, trigger, execution.getId(), execution.getStatus());
        } catch (Exception ex) {
            log.error("Batch job {} failed to start from {}: {}", jobName, trigger, ex.getMessage(), ex);
        }
        return CompletableFuture.completedFuture(null);
    }
}