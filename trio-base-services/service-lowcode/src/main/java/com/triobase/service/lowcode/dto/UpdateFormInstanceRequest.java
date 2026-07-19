package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateFormInstanceRequest {
    private Map<String, Object> data;
}
