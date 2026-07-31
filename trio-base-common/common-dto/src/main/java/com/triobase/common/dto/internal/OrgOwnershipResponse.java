package com.triobase.common.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrgOwnershipResponse {
    private String tenantId;
    private String userId;
    private String primaryOrgUnitId;
    private boolean resolved;
}
