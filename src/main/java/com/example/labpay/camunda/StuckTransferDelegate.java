package com.example.labpay.camunda;

import com.example.labpay.batch.BatchJobStartService;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("stuckTransferDelegate")
@RequiredArgsConstructor
public class StuckTransferDelegate implements JavaDelegate {

    private final BatchJobStartService batchJobStartService;

    @Override
    public void execute(DelegateExecution execution) {
        String trigger = trigger(execution);
        batchJobStartService.startAsync("stuckTransferJob", trigger);
        execution.setVariable("batchJobName", "stuckTransferJob");
        execution.setVariable("batchStartMode", "async");
    }

    private String trigger(DelegateExecution execution) {
        Object value = execution.getVariable("trigger");
        return value == null ? "camunda-timer" : String.valueOf(value);
    }
}