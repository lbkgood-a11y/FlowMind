package com.triobase.service.openapi.domain.model;

import com.triobase.common.openapi.entity.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantEntity extends VersionedEntity {

    private String tenantId;
}
