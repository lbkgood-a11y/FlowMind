package com.triobase.service.openapi.dto;

import java.util.List;
import java.util.Map;

public record LifecycleReadinessResponse(
        boolean ready,
        boolean publicRuntimeEnabled,
        Map<String, Long> assetCounts,
        List<ReadinessStage> stages,
        List<String> blockers) {
    public record ReadinessStage(String key, String title, boolean ready, String route) { }
}
