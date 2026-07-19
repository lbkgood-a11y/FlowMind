package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName(value = "oa_execution_step_attempt", autoResultMap = true)
public class ExecutionStepAttempt {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String executionId;
    private String stepKey;
    private String stepType;
    private Integer attemptNumber;
    private String attemptState;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMillis;
    private Integer externalStatus;
    private String errorCode;
    private String sanitizedError;
    private String actionId;
    private String actionType;
    private String actionSource;
    private String actionActorType;
    private String actionActorId;
    private String actionActorName;
    private String actionTraceId;
    private String actionCorrelationId;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode evidence;
    private LocalDateTime createdAt;
}
