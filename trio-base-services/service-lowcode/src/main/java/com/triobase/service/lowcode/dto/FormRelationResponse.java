package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class FormRelationResponse {
    private String id;
    private String relationCode;
    private String parentFormDefinitionId;
    private String childFormDefinitionId;
    private String cardinality;
    private String parentKeyField;
    private String childForeignKeyField;
    private Boolean cascadeSave;
    private Boolean cascadeDelete;
    private Integer sortOrder;
    private String childFormKey;
    private String childFormName;
    private String childSchemaJson;
    private String childUiSchemaJson;
}
