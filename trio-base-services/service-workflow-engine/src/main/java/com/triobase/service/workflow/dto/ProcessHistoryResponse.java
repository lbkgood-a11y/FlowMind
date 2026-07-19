package com.triobase.service.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProcessHistoryResponse {
    private List<NodeHistoryItem> nodes;
    private List<TaskOperationItem> operations;

    @Data
    public static class NodeHistoryItem {
        private String id;
        private String nodeId;
        private String nodeName;
        private String nodeType;
        private Integer visitNo;
        private String status;
        private String assigneeSnapshot;
        private String result;
        private LocalDateTime enteredAt;
        private LocalDateTime exitedAt;
    }

    @Data
    public static class TaskOperationItem {
        private String operationId;
        private String sourceTaskId;
        private String targetTaskId;
        private String action;
        private String operatorId;
        private String operatorName;
        private String targetUserId;
        private String targetUserName;
        private String targetNodeId;
        private String comment;
        private String status;
        private String traceId;
        private String resultJson;
        private String actionId;
        private String actionType;
        private String actionSource;
        private String actionActorType;
        private String actionActorId;
        private String actionActorName;
        private String actionCorrelationId;
        private LocalDateTime createdAt;
    }
}
