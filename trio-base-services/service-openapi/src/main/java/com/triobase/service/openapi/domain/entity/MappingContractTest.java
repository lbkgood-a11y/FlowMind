package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.entity.BaseEntity;
import com.triobase.service.openapi.infrastructure.persistence.PostgresJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "oa_mapping_contract_test", autoResultMap = true)
public class MappingContractTest extends BaseEntity {
    private String mappingVersionId;
    private String testName;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode inputPayload;
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode expectedOutput;
    private String expectedErrorCode;
    private Boolean requiredTest;
    private Boolean enabled;
}
