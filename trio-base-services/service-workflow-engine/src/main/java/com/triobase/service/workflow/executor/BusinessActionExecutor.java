package com.triobase.service.workflow.executor;

public interface BusinessActionExecutor {

    String executorKey();

    BusinessActionResult execute(BusinessActionContext context);
}
