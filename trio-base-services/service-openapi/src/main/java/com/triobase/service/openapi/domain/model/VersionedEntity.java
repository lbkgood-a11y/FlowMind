package com.triobase.service.openapi.domain.model;

import com.baomidou.mybatisplus.annotation.Version;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class VersionedEntity extends BaseEntity {

    @Version
    private Long rowVersion;
}
