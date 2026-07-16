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
@TableName(value = "oa_structure_version", autoResultMap = true)
public class StructureVersion extends VersionedEntity {
    private String structureId;
    private Integer versionNumber;
    private Integer compatibilityLine;
    private VersionLifecycleState lifecycleState;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode schemaContent;
    private String schemaHash;
    private String parentStructureVersionId;
    private String changeSummary;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode semanticChange;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode compatibilityResult;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime deprecatedAt;
    private LocalDateTime archivedAt;
}
