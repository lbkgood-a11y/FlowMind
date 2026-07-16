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
@TableName(value = "oa_structure_provenance", autoResultMap = true)
public class StructureProvenance {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String structureVersionId;
    private String sourceType;
    private String sourceName;
    private String sourceLocation;
    private String documentHash;
    private String importedOperation;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode metadata;
    private String createdBy;
    private LocalDateTime createdAt;
}
