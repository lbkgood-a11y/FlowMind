package com.triobase.service.workflow.executor;

public interface AgentFollowUpExecutor {

    String executorKey();

    AgentFollowUpResult execute(AgentFollowUpContext context);
}
