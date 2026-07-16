package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.model.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_value_map_set")
public class ValueMapSet extends TenantEntity {
    private String valueMapKey;
    private String displayName;
    private String description;
    private String ownerId;
    private AssetLifecycleState lifecycleState;
}
