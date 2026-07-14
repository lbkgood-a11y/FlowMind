package com.triobase.service.workflow.dto;

public record ProcessVersionConflictResponse(
        String requestedProcessPackageId,
        Integer requestedVersion,
        String currentProcessPackageId,
        Integer currentVersion) {
}
