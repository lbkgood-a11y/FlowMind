package com.triobase.service.openapi.dto;

import java.util.List;

public record OpenApiExportRequest(
        String title,
        String apiVersion,
        String serverUrl,
        List<ExportOperation> operations) {

    public record ExportOperation(
            String path,
            String method,
            String operationId,
            String requestStructureVersionId,
            String responseStructureVersionId) {
    }
}
