package com.triobase.service.apiruntime.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * 集成编排的受治理 I/O 边界。
 *
 * <p>连接器调用和 Owner Action 可能被 Temporal 重试，必须使用执行 ID 与步骤 ID 作为幂等身份；
 * 补偿也必须可重复执行。实现不得绕过已发布路由、权限过滤或 owner-hosted Action 契约。</p>
 */
@ActivityInterface
public interface IntegrationOrchestrationActivities {

    @ActivityMethod
    String loadRelease(String commandJson);

    @ActivityMethod
    String transform(String stepCommandJson);

    @ActivityMethod
    String invokeConnector(String stepCommandJson);

    @ActivityMethod
    String invokeOwnerAction(String stepCommandJson);

    @ActivityMethod
    String persistExecution(String stateCommandJson);

    @ActivityMethod
    String persistWait(String waitCommandJson);

    @ActivityMethod
    String compensate(String compensationCommandJson);
}
