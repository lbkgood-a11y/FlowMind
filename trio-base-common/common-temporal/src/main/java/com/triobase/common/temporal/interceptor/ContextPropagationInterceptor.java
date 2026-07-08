package com.triobase.common.temporal.interceptor;

import io.temporal.common.interceptors.WorkflowInboundCallsInterceptor;
import io.temporal.common.interceptors.WorkflowInboundCallsInterceptorBase;
import io.temporal.common.interceptors.WorkflowOutboundCallsInterceptor;
import io.temporal.common.interceptors.WorkflowOutboundCallsInterceptorBase;

/**
 * TraceId 跨 Temporal 边界透传拦截器 — 铁律 8。
 * Phase 1 预置骨架，后续 Workflow → Activity 链路启用时完善 Header 读写逻辑。
 */
public final class ContextPropagationInterceptor {

    private ContextPropagationInterceptor() {
    }

    public static WorkflowInboundCallsInterceptor inbound(WorkflowInboundCallsInterceptor next) {
        return new WorkflowInboundCallsInterceptorBase(next) {
            @Override
            public void init(WorkflowOutboundCallsInterceptor outbound) {
                super.init(outbound);
            }

            @Override
            public WorkflowOutput execute(WorkflowInput input) {
                return super.execute(input);
            }
        };
    }

    public static WorkflowOutboundCallsInterceptor outbound(WorkflowOutboundCallsInterceptor next) {
        return new WorkflowOutboundCallsInterceptorBase(next) {
        };
    }
}
