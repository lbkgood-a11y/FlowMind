package com.triobase.data.analytics.dto;

import lombok.Data;

import java.util.List;

@Data
public class SemanticQueryResponse {
    private String status;
    private String collectionKey;
    private int topK;
    private long elapsedMs;
    private List<SemanticChunkResponse> chunks;
}
