package com.triobase.service.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessPackageResponse {
    private String id;
    private String processKey;
    private String name;
    private String category;
    private String description;
    private Integer version;
    private String status;
    private String processJson;
    private String formSchema;
    private String formUiSchema;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
