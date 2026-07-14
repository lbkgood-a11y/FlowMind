package com.triobase.data.analytics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HybridQueryRequest {
    @NotBlank
    private String mode = "HYBRID";
    @Valid
    private StructuredQueryRequest structured;
    @Valid
    private SemanticQueryRequest semantic;
}
