package com.example.labpay.camunda;

import com.example.labpay.batch.BatchJobRunner;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("cardSessionCleanupDelegate")
public class CardSessionCleanupDelegate implements JavaDelegate {

    private final BatchJobRunner batchJobRunner;

    public CardSessionCleanupDelegate(BatchJobRunner batchJobRunner) {
        this.batchJobRunner = batchJobRunner;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        batchJobRunner.run("cardSessionCleanupJob", "camunda-timer");
    }
}
