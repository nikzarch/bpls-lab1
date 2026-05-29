package com.example.labpay.camunda;

import com.example.labpay.batch.BatchJobRunner;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component("maintenanceDelegate")
public class MaintenanceDelegate {

    private final BatchJobRunner batchJobRunner;

    public MaintenanceDelegate(BatchJobRunner batchJobRunner) {
        this.batchJobRunner = batchJobRunner;
    }

    public void runBankReconciliation(DelegateExecution execution) throws Exception {
        launch("bankReconciliationJob", "camunda-timer");
    }

    public void runHoldExpiration(DelegateExecution execution) throws Exception {
        launch("holdExpirationJob", "camunda-timer");
    }

    public void runCardSessionCleanup(DelegateExecution execution) throws Exception {
        launch("cardSessionCleanupJob", "camunda-timer");
    }

    public void runStuckTransfer(DelegateExecution execution) throws Exception {
        launch("stuckTransferJob", "camunda-timer");
    }

    private void launch(String jobName, String trigger) throws Exception {
        batchJobRunner.run(jobName, trigger);
    }
}
