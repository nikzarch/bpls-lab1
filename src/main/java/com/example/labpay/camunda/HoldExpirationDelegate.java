package com.example.labpay.camunda;

import com.example.labpay.batch.BatchJobRunner;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("holdExpirationDelegate")
public class HoldExpirationDelegate implements JavaDelegate {

    private final BatchJobRunner batchJobRunner;

    public HoldExpirationDelegate(BatchJobRunner batchJobRunner) {
        this.batchJobRunner = batchJobRunner;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        batchJobRunner.run("holdExpirationJob", "camunda-timer");
    }
}
