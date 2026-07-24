package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FormInstanceGraphResponse {
    private FormInstanceResponse instance;
    private Map<String, List<FormInstanceGraphResponse>> children;
}
