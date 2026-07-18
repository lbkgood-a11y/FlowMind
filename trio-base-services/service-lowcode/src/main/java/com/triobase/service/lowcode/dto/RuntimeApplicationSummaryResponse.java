package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RuntimeApplicationSummaryResponse {
    private String tenantId;
    private String appKey;
    private String name;
    private String description;
    private String versionId;
    private Integer version;
    private String formKey;
    private Integer formVersion;
    private String schemaHash;
    private String metadataHash;
    private LocalDateTime publishedAt;
}
