package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class FormFieldSchemaRequest {
    private String fieldKey;
    private String label;
    private String fieldType;
    private Boolean required;
    private String defaultValue;
    private String placeholder;
    private String optionsJson;
    private Integer sortOrder;
}
