package com.triobase.data.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DatasetFieldRequest {
    @NotBlank
    private String fieldKey;
    @NotBlank
    private String label;
    @NotBlank
    private String fieldType;
    private Boolean searchable;
    private Boolean sortable;
    private Integer sortOrder;
}
