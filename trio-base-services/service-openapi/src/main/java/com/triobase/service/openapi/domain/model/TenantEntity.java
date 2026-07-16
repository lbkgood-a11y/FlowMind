package com.triobase.service.openapi.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantEntity extends VersionedEntity {

    private String tenantId;
}
