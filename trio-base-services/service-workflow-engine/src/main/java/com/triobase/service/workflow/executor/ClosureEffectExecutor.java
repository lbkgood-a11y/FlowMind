package com.triobase.service.workflow.executor;

public interface ClosureEffectExecutor {

    String executorKey();

    ClosureEffectResult execute(ClosureEffectContext context);
}
