package com.triobase.service.workflow.workflow;

import com.triobase.service.workflow.dto.AddSignTaskCommand;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.RejectTaskCommand;
import com.triobase.service.workflow.dto.TaskActionCommand;
import com.triobase.service.workflow.dto.TransferTaskCommand;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ProcessWorkflow {

    @WorkflowMethod
    void startProcess(ProcessPackageDefinition packageDef,
                      String instanceId,
                      String initiatorId,
                      String initiatorName,
                      String formDataJson);

    @SignalMethod
    void approveTask(TaskActionCommand command);

    @SignalMethod
    void rejectTask(RejectTaskCommand command);

    @SignalMethod
    void transferTask(TransferTaskCommand command);

    @SignalMethod
    void addSignTask(AddSignTaskCommand command);
}
