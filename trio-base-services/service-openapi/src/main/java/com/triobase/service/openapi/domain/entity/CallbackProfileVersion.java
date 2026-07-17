package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CallbackCorrelationType;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.domain.model.VersionedEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "oa_callback_profile_version", autoResultMap = true)
public class CallbackProfileVersion extends VersionedEntity {
    private String callbackProfileId;
    private Integer versionNumber;
    private VersionLifecycleState lifecycleState;
    private Environment environment;
    private String applicationClientId;
    private AuthenticationType authenticationType;
    private String secretReference;
    private String requestStructureVersionId;
    private String inboundMappingVersionId;
    private String partnerEventIdPointer;
    private String correlationPointer;
    private CallbackCorrelationType correlationType;
    private String signalName;
    private Long replayWindowSeconds;
    private Long maxBodyBytes;
    private Long callbackPerMinute;
    private Integer acknowledgementStatus;
    private String acknowledgementContentType;
    private String acknowledgementBody;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode securityPolicy;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode validationResult;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
