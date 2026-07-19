package com.triobase.service.openapi.service.authorization;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReferenceContractDocument {
    private String id;
    private String tenantId;
    private String status;
    private String ownerUserId;
    private String ownerOrgId;
    private String submittedBy;
    private BigDecimal amount;
}
