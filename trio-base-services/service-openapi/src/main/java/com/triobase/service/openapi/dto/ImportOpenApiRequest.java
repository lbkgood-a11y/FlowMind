package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.StructureKind;

public record ImportOpenApiRequest(
        byte[] document,
        String sourceName,
        String tenantId,
        String namespace,
        StructureKind structureKind,
        String ownerType,
        String ownerId) {
}
