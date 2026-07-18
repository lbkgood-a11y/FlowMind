package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ApplicationResponse {
    private String id;
    private String tenantId;
    private String appKey;
    private String name;
    private String description;
    private String status;
    private Integer latestVersion;
    private String latestPublishedVersionId;
    private String versionId;
    private Integer version;
    private String primaryFormDefinitionId;
    private String formKey;
    private Integer formVersion;
    private String schemaHash;
    private String viewPermissionCode;
    private String metadataHash;
    private LocalDateTime publishedAt;
    private LocalDateTime offlineAt;
    private LocalDateTime createdAt;
    private List<ApplicationPageResponse> pages;
    private List<ApplicationActionResponse> actions;
}
