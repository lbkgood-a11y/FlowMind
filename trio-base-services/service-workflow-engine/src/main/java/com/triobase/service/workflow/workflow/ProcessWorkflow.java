package com.triobase.service.workflow.workflow;

import com.triobase.service.workflow.dto.AddSignTaskCommand;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.RejectTaskCommand;
import com.triobase.service.workflow.dto.TaskActionCommand;
import com.triobase.service.workflow.dto.TransferTaskCommand;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * 审批流程的 Temporal 契约。
 *
 * <p>Workflow 输入必须是可稳定序列化的流程快照；运行中的审批、驳回、转办和加签通过
 * Signal 驱动状态推进。Signal 命令必须携带稳定 operationId，使重放和重复投递不会产生
 * 第二次业务副作用。</p>
 */
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
