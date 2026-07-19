package com.triobase.common.dto.catalog;

import lombok.Data;

@Data
public class BusinessStatusMetadata {
    private String statusCode;
    private String displayName;
    private String statusGroup;
    private boolean initial;
    private boolean terminal;
    private Integer sortOrder;
}
