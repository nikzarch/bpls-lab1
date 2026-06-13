package com.example.labpay.camunda.state;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.springframework.stereotype.Component;

@Component("assignTaskToUsernameListener")
public class AssignTaskToUsernameListener implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        Object username = delegateTask.getExecution().getVariable("username");
        if (username != null && !String.valueOf(username).isBlank()) {
            delegateTask.setAssignee(String.valueOf(username));
        }
    }
}