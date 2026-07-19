package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class RuntimeFieldAuthorizationResponse {
    private String fieldKey;
    private String readMode;
    private String writeMode;
    private String maskStrategy;
    private String reasonCode;
    private String reasonMessage;
}
