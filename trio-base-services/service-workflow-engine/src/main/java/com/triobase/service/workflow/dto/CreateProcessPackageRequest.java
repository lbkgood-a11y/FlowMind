package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class CreateProcessPackageRequest {
    private String processKey;
    private String name;
    private String category;
    private String description;
    private String processJson;
}

@Data
class UpdateProcessPackageRequest {
    private String name;
    private String description;
    private String processJson;
}
