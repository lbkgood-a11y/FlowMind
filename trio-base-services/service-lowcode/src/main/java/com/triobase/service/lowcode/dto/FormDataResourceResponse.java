package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class FormDataResourceResponse {
    private String resourceCode;
    private String resourceName;
    private String resourceType;
    private String businessObjectId;
    private String formKey;
    private Integer version;
}
