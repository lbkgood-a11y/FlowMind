package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class UpdateProcessPackageRequest {
    private String name;
    private String category;
    private String description;
    private String processJson;
    private String formDefinitionId;
}
