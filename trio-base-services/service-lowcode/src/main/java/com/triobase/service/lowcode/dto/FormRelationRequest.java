package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class FormRelationRequest {
    private String relationCode;
    private String parentFormDefinitionId;
    private String childFormDefinitionId;
    private String cardinality;
    private String parentKeyField;
    private String childForeignKeyField;
    private Boolean cascadeSave;
    private Boolean cascadeDelete;
    private Integer sortOrder;
}
