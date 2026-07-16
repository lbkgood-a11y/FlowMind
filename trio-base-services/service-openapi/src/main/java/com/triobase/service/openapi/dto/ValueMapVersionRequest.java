package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.UnmappedValuePolicy;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ValueMapVersionRequest(
        boolean caseSensitive,
        @NotNull UnmappedValuePolicy unmappedPolicy,
        String defaultCanonicalValue,
        String defaultExternalValue,
        List<CreateValueMapRequest.Entry> entries) {
}
