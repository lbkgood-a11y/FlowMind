package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_operation_audit_log")
public class SysOperationAuditLog extends BaseEntity {
    private String tenantId;
    private String userId;
    private String username;
    private String permissionCode;
    private String moduleName;
    private String actionName;
    private String resourceId;
    private String httpMethod;
    private String requestPath;
    private String queryString;
    private String clientIp;
    private String userAgent;
    private String requestSummary;
    private String responseSummary;
    private String resultStatus;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionStatus;
    private String actionTargetType;
    private String actionTargetId;
    private String actionCorrelationId;
    private String actionIdempotencyKey;
    private String actionSummary;
    private Integer statusCode;
    private String errorMessage;
    private Long latencyMs;
    private String traceId;
    private LocalDateTime operatedAt;
}
