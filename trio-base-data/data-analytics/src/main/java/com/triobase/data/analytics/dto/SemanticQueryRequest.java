package com.triobase.data.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SemanticQueryRequest {
    private String collectionKey;
    @NotBlank
    private String query;
    private int topK = 5;
}
