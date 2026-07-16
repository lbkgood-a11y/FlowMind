package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.domain.model.VersionedEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "oa_orchestration_version", autoResultMap = true)
public class OrchestrationVersion extends VersionedEntity {
    private String orchestrationDefinitionId;
    private Integer versionNumber;
    private VersionLifecycleState lifecycleState;
    private String definitionSchemaVersion;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode definitionContent;
    private String definitionHash;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode validationResult;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
