package com.triobase.data.analytics.dto;

import lombok.Data;

@Data
public class SemanticChunkResponse {
    private String documentId;
    private String title;
    private String collectionKey;
    private int chunkIndex;
    private String content;
    private double score;
}
