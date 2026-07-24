package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class NestedFormInstanceRequest {
    private String formDefinitionId;
    private Map<String, Object> data;
    private Map<String, List<NestedFormInstanceRequest>> children;
}
