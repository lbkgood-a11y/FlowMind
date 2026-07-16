package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.StructureDirection;
import com.triobase.service.openapi.domain.enums.StructureKind;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

import java.time.LocalDateTime;

public record StructureResponse(
        String id,
        String tenantId,
        String namespace,
        String structureKey,
        String displayName,
        String description,
        StructureKind structureKind,
        StructureDirection direction,
        String ownerType,
        String ownerId,
        AssetLifecycleState lifecycleState,
        Integer latestVersion,
        String latestVersionId,
        VersionLifecycleState latestVersionState,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
