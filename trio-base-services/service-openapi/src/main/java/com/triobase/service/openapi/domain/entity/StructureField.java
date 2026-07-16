package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.SensitivityLevel;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "oa_structure_field", autoResultMap = true)
public class StructureField {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String structureVersionId;
    private String jsonPointer;
    private String fieldName;
    private String dataType;
    private Boolean requiredField;
    private Boolean arrayField;
    private String semanticId;
    private SensitivityLevel sensitivityLevel;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode fieldConstraints;
    private Integer ordinal;
    private LocalDateTime createdAt;
}
