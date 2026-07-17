package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "oa_callback_inbox", autoResultMap = true)
public class CallbackInbox {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String applicationClientId;
    private String callbackProfileVersionId;
    private String partnerEventId;
    private String correlationValue;
    private String executionId;
    private CallbackInboxState inboxState;
    private String bodyHash;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode mappedPayload;
    private String signalName;
    private Integer signalAttempts;
    private LocalDateTime nextSignalAt;
    private String lastSignalError;
    private String quarantineReason;
    private String resolutionState;
    private String resolutionNote;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime retentionUntil;
    private LocalDateTime updatedAt;
}
