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
public class CardSessionCleanupScheduler {

    private final BatchJobRunner batchJobRunner;

    @Scheduled(cron = "${app.batch.card-session-cleanup.cron}")
    public void cleanupAutomatically() {
        try {
            var execution = batchJobRunner.run("cardSessionCleanupJob", "scheduled");
            log.info("cardSessionCleanupJob executionId={} status={}",
                    execution.getId(), execution.getStatus());
        } catch (Exception e) {
            log.error("cardSessionCleanupJob failed to launch: {}", e.getMessage(), e);
        }
    }
}