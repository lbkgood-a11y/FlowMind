package com.triobase.common.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublishedFormSnapshotResponse {
    private String formDefinitionId;
    private String tenantId;
    private String formKey;
    private Integer version;
    private String schemaHash;
    private String schemaJson;
    private String uiSchemaJson;
    private LocalDateTime publishedAt;
}
