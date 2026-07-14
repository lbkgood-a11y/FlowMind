package com.triobase.service.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessObjectSummaryResponse {
    private String id;
    private String tenantId;
    private String typeCode;
    private String displayName;
    private String serviceCode;
    private Integer version;
    private String status;
    private String description;
}
