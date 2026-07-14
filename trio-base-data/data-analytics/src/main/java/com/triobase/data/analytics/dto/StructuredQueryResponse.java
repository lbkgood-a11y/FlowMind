package com.triobase.data.analytics.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StructuredQueryResponse {
    private String status;
    private String datasetKey;
    private long total;
    private int page;
    private int size;
    private long elapsedMs;
    private List<DatasetFieldResponse> fields;
    private List<Map<String, Object>> rows;
}
