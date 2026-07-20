package com.triobase.service.tenant.dto;

import lombok.Data;

@Data
public class UpdateTenantStatusRequest {
    private String status;
    private String reason;
}
