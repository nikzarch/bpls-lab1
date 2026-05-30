package com.example.labpay.camunda;

import com.example.labpay.batch.BatchJobStartService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component("maintenanceDelegate")
public class MaintenanceDelegate {

    private final BatchJobStartService batchJobStartService;

    public MaintenanceDelegate(BatchJobStartService batchJobStartService) {
        this.batchJobStartService = batchJobStartService;
    }

    public void runBankReconciliation(DelegateExecution execution) {
        launch(execution, "bankReconciliationJob");
    }

    public void runHoldExpiration(DelegateExecution execution) {
        launch(execution, "holdExpirationJob");
    }

    public void runCardSessionCleanup(DelegateExecution execution) {
        launch(execution, "cardSessionCleanupJob");
    }

    public void runStuckTransfer(DelegateExecution execution) {
        launch(execution, "stuckTransferJob");
    }

    private void launch(DelegateExecution execution, String jobName) {
        Object trigger = execution.getVariable("trigger");
        batchJobStartService.startAsync(jobName, trigger == null ? "camunda-timer" : String.valueOf(trigger));
        execution.setVariable("batchJobName", jobName);
        execution.setVariable("batchStartMode", "async");
    }
}