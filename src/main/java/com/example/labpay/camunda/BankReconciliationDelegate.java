package com.example.labpay.camunda;

import com.example.labpay.batch.BatchJobRunner;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("bankReconciliationDelegate")
public class BankReconciliationDelegate implements JavaDelegate {

    private final BatchJobRunner batchJobRunner;

    public BankReconciliationDelegate(BatchJobRunner batchJobRunner) {
        this.batchJobRunner = batchJobRunner;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        batchJobRunner.run("bankReconciliationJob", "camunda-timer");
    }
}
