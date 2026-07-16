package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionMode;
import com.triobase.service.openapi.domain.enums.ExecutionState;
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
