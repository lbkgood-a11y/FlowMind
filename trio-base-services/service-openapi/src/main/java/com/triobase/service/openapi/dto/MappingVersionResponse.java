package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.MappingDirection;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

import java.util.List;

public record MappingVersionResponse(
        String mappingSetId,
        String mappingVersionId,
        String tenantId,
        String mappingKey,
        String displayName,
        MappingDirection direction,
        Integer versionNumber,
        VersionLifecycleState lifecycleState,
        String sourceStructureVersionId,
        String targetStructureVersionId,
        JsonNode coverageResult,
        List<MappingRuleRequest> rules) {
}
