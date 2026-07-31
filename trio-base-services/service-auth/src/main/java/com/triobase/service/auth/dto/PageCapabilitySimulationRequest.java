package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PageCapabilitySimulationRequest {
    private String tenantId;
    private String mode;
    private String userId;
    private String roleId;
    private String businessObjectId;
    private List<String> organizationIds = new ArrayList<>();
}
