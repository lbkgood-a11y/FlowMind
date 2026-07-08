package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FormDefinitionResponse {
    private String id;
    private String formKey;
    private String name;
    private String description;
    private Integer version;
    private String status;
    private String schemaJson;
    private String uiSchemaJson;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<FormFieldSchemaRequest> fields;
}
