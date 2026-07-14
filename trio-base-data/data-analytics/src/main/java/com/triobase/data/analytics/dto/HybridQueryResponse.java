package com.triobase.data.analytics.dto;

import lombok.Data;

@Data
public class HybridQueryResponse {
    private String mode;
    private StructuredQueryResponse structured;
    private SemanticQueryResponse semantic;
    private long elapsedMs;
}
