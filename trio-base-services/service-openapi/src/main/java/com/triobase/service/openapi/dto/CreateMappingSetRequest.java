package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.MappingDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateMappingSetRequest(
        String tenantId,
        @NotBlank String mappingKey,
        @NotBlank String displayName,
        String description,
        @NotNull MappingDirection direction,
        @NotBlank String canonicalStructureId,
        @NotBlank String externalStructureId,
        @NotBlank String sourceStructureVersionId,
        @NotBlank String targetStructureVersionId,
        @NotBlank String ownerId,
        List<MappingRuleRequest> rules) {
}
