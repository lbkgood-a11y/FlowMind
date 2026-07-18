package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RuntimeActionRequest {
    private String title;
    private String idempotencyKey;
    private Map<String, Object> data;
}
