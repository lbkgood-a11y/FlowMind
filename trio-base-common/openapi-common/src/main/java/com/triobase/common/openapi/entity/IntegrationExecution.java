package com.triobase.common.openapi.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.triobase.common.openapi.enums.Environment;
import com.triobase.common.openapi.enums.ExecutionMode;
import com.triobase.common.openapi.enums.ExecutionState;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("oa_execution")
public class IntegrationExecution {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private Environment environment;
    private String applicationClientId;
    private String routeDefinitionId;
    private String releaseSnapshotId;
    private ExecutionMode executionMode;
    private ExecutionState executionState;
    private String workflowId;
    private String workflowRunId;
    private String idempotencyKey;
    private String traceId;
    private String callerId;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionActorType;
    private String actionActorId;
    private String actionActorName;
    private String actionTraceId;
    private String actionCorrelationId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMillis;
    private String errorCode;
    private String sanitizedError;
    private Boolean diagnosticEnabled;
    private LocalDateTime diagnosticExpiresAt;
    private LocalDateTime retentionUntil;
    @Version
    private Long rowVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
