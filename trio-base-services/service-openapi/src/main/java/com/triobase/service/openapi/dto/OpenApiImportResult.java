package com.triobase.service.openapi.dto;

import java.util.List;

public record OpenApiImportResult(
        String openApiVersion,
        String sourceHash,
        List<StructureResponse> structures) {
}
