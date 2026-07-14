package com.triobase.data.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class StructuredQueryRequest {
    @NotBlank
    private String datasetKey;
    private Map<String, Object> filters = new HashMap<>();
    private int page = 1;
    private int size = 20;
}
