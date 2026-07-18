package com.triobase.service.lowcode.dto;

import lombok.Data;

@Data
public class ApplicationPageRequest {
    private String pageType;
    private String metadataJson;
    private Integer sortOrder;
}
