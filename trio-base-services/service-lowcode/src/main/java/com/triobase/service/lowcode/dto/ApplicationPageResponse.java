package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class ApplicationPageResponse {
    private String id;
    private String pageType;
    private String metadataJson;
    private Integer sortOrder;
}
