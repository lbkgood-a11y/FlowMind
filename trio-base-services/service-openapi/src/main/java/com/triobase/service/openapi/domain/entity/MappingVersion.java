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
@TableName(value = "oa_mapping_version", autoResultMap = true)
public class MappingVersion extends VersionedEntity {
    private String mappingSetId;
    private Integer versionNumber;
    private String sourceStructureVersionId;
    private String targetStructureVersionId;
    private VersionLifecycleState lifecycleState;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode coverageResult;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode compiledPlan;
    private String compiledPlanHash;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
