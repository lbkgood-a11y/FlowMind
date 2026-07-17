package com.triobase.service.openapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record LifecycleAssetItem(
        String id,
        String assetType,
        String assetKey,
        String displayName,
        String lifecycleState,
        String tenantId,
        String createdAt,
        String updatedAt,
        JsonNode detail) {
}
