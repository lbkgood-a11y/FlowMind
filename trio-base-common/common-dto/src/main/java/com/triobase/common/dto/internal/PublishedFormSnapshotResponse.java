package com.triobase.common.dto.internal;

import lombok.Data;

@Data
public class PublishedFormSnapshotResponse {
    private String formDefinitionId;
    private String formKey;
    private Integer version;
    private String schemaJson;
    private String uiSchemaJson;
}
