package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateFormDefinitionRequest {
    private String formKey;
    private String name;
    private String description;
    private String schemaJson;
    private String uiSchemaJson;
    private List<FormFieldSchemaRequest> fields;
}
