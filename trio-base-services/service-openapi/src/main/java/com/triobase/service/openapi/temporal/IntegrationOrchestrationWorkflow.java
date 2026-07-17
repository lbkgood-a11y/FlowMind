package com.triobase.service.openapi.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface IntegrationOrchestrationWorkflow {

    @WorkflowMethod
    String run(String commandJson);

    @SignalMethod
    void receiveSignal(String signalJson);

    @SignalMethod
    void requestCancel(String reason);

    @QueryMethod
    String status();

    @QueryMethod
    String result();
}
