package com.triobase.data.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IngestDocumentRequest {
    private String datasetId;
    @NotBlank
    private String collectionKey;
    private String sourceKey;
    @NotBlank
    private String title;
    @NotBlank
    private String content;
}
