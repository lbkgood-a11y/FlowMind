package com.triobase.service.workflow.executor;

import java.util.Map;

public record AgentFollowUpResult(
        boolean success,
        String resultCode,
        String summary,
        String failureCategory,
        Map<String, Object> data) {

    public static AgentFollowUpResult succeeded(String resultCode, String summary,
                                                Map<String, Object> data) {
        return new AgentFollowUpResult(true, resultCode, summary, null, data);
    }

    public static AgentFollowUpResult failed(String resultCode, String failureCategory,
                                             String summary) {
        return new AgentFollowUpResult(false, resultCode, summary, failureCategory, Map.of());
    }
}
