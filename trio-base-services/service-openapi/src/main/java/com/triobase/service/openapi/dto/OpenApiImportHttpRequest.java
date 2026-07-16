package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.StructureKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.nio.charset.StandardCharsets;

public record OpenApiImportHttpRequest(
        @NotBlank String document,
        @NotBlank String sourceName,
        String tenantId,
        @NotBlank String namespace,
        @NotNull StructureKind structureKind,
        @NotBlank String ownerType,
        @NotBlank String ownerId) {

    public ImportOpenApiRequest toServiceRequest() {
        return new ImportOpenApiRequest(
                document.getBytes(StandardCharsets.UTF_8), sourceName, tenantId,
                namespace, structureKind, ownerType, ownerId);
    }
}
