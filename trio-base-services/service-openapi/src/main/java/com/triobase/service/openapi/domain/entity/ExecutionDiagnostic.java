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
@TableName(value = "oa_execution_diagnostic", autoResultMap = true)
public class ExecutionDiagnostic {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String executionId;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode requestPayload;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode responsePayload;
    private String capturedBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
