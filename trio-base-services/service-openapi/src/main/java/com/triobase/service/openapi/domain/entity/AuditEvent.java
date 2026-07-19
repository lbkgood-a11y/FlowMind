package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName(value = "oa_audit_event", autoResultMap = true)
public class AuditEvent {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String actorId;
    private String actorType;
    private String action;
    private String resourceType;
    private String resourceId;
    private Environment environment;
    private String outcome;
    private String reason;
    private String traceId;
    private String sourceIp;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionActorType;
    private String actionActorId;
    private String actionActorName;
    private String actionTraceId;
    private String actionCorrelationId;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode changeSummary;
    private LocalDateTime createdAt;
}
