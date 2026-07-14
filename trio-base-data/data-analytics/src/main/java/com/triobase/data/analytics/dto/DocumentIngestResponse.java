package com.triobase.data.analytics.dto;

import lombok.Data;

@Data
public class DocumentIngestResponse {
    private String documentId;
    private String collectionKey;
    private String sourceKey;
    private String title;
    private int chunkCount;
}
