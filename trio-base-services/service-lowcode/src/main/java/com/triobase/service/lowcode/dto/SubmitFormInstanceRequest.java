package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SubmitFormInstanceRequest {
    private String submittedBy;
    private Map<String, Object> data;
}
