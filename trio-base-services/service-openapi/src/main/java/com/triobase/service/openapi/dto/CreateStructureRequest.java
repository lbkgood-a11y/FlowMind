package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.StructureDirection;
import com.triobase.service.openapi.domain.enums.StructureKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStructureRequest {
    private String tenantId;
    @NotBlank
    @Size(max = 128)
    private String namespace;
    @NotBlank
    @Size(max = 128)
    private String structureKey;
    @NotBlank
    @Size(max = 256)
    private String displayName;
    @Size(max = 1024)
    private String description;
    @NotNull
    private StructureKind structureKind;
    @NotNull
    private StructureDirection direction;
    @NotBlank
    private String ownerType;
    @NotBlank
    private String ownerId;
    private String parentStructureVersionId;
    @NotNull
    private JsonNode schemaContent;
    @Size(max = 1024)
    private String changeSummary;
}
