package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.MappingDirection;
import com.triobase.service.openapi.domain.model.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_mapping_set")
public class MappingSet extends TenantEntity {
    private String mappingKey;
    private String displayName;
    private String description;
    private MappingDirection direction;
    private String canonicalStructureId;
    private String externalStructureId;
    private String ownerId;
    private AssetLifecycleState lifecycleState;
}
