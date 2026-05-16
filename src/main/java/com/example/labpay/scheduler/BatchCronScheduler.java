package com.example.labpay.scheduler;

import com.example.labpay.batch.BatchJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({"worker", "all"})
@RequiredArgsConstructor
public class BatchCronScheduler {

    private final BatchJobRunner batchJobRunner;

    @Scheduled(cron = "${app.batch.bank-reconciliation.cron}")
    public void reconcileBankOperations() {
        launch("bankReconciliationJob");
    }

    @Scheduled(cron = "${app.batch.hold-expiration.cron}")
    public void expireHolds() {
        launch("holdExpirationJob");
    }

    @Scheduled(cron = "${app.batch.stuck-transfer.cron}")
    public void reconcileStuckTransfers() {
        launch("stuckTransferJob");
    }

    private void launch(String jobName) {
        try {
            var execution = batchJobRunner.run(jobName, "scheduled");
            log.info("{} executionId={} status={}", jobName, execution.getId(), execution.getStatus());
        } catch (Exception e) {
            log.error("{} failed to launch: {}", jobName, e.getMessage(), e);
        }
    }
}