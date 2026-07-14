package com.triobase.data.analytics.dto;

import lombok.Data;

@Data
public class DatasetFieldResponse {
    private String id;
    private String fieldKey;
    private String label;
    private String fieldType;
    private Boolean searchable;
    private Boolean sortable;
    private Integer sortOrder;
}
