package com.triobase.service.openapi.dto;

import com.triobase.common.openapi.mapping.MappingRuleRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateMappingDraftRequest(
        @NotBlank String sourceStructureVersionId,
        @NotBlank String targetStructureVersionId,
        List<MappingRuleRequest> rules) {
}
