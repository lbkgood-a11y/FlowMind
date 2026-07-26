package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.entity.BaseEntity;
import com.triobase.common.openapi.mapping.MappingOperation;
import com.triobase.common.openapi.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "oa_mapping_rule", autoResultMap = true)
public class MappingRule extends BaseEntity {
    private String mappingVersionId;
    private Integer ruleOrder;
    private MappingOperation operationType;
    private String sourcePointer;
    private String targetPointer;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode operationConfig;
    private Boolean requiredRule;
}
