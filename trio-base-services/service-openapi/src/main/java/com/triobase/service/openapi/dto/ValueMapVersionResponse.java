package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.UnmappedValuePolicy;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;

import java.util.List;

public record ValueMapVersionResponse(
        String valueMapSetId,
        String valueMapVersionId,
        String tenantId,
        String valueMapKey,
        String displayName,
        String ownerId,
        Integer versionNumber,
        VersionLifecycleState lifecycleState,
        boolean caseSensitive,
        UnmappedValuePolicy unmappedPolicy,
        String defaultCanonicalValue,
        String defaultExternalValue,
        List<CreateValueMapRequest.Entry> entries) {
}
