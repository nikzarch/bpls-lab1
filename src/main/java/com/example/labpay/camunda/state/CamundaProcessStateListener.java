package com.example.labpay.camunda.state;

import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.springframework.stereotype.Component;

@Component("camundaProcessStateListener")
@RequiredArgsConstructor
public class CamundaProcessStateListener implements ExecutionListener, TaskListener {

    private final ProcessStateService processStateService;

    @Override
    public void notify(DelegateExecution execution) {
        processStateService.onExecutionEvent(execution);
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String event = delegateTask.getEventName();

        if (TaskListener.EVENTNAME_CREATE.equals(event)) {
            processStateService.onUserTaskCreate(delegateTask);
            return;
        }

        if (TaskListener.EVENTNAME_COMPLETE.equals(event)) {
            processStateService.onUserTaskComplete(delegateTask);
        }
    }
}
