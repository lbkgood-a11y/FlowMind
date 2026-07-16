package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.StructureDirection;
import com.triobase.service.openapi.domain.enums.StructureKind;
import com.triobase.service.openapi.domain.model.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_structure")
public class OpenApiStructure extends TenantEntity {
    private String namespace;
    private String structureKey;
    private String displayName;
    private String description;
    private StructureKind structureKind;
    private String dataFormat;
    private StructureDirection direction;
    private String ownerType;
    private String ownerId;
    private AssetLifecycleState lifecycleState;
}
