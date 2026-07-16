package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.UnmappedValuePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateValueMapRequest(
        String tenantId,
        @NotBlank String valueMapKey,
        @NotBlank String displayName,
        String description,
        @NotBlank String ownerId,
        boolean caseSensitive,
        @NotNull UnmappedValuePolicy unmappedPolicy,
        String defaultCanonicalValue,
        String defaultExternalValue,
        List<Entry> entries) {

    public record Entry(@NotBlank String canonicalValue, @NotBlank String externalValue, int order) {
    }
}
