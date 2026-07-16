package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.service.openapi.domain.enums.UnmappedValuePolicy;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.domain.model.VersionedEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_value_map_version")
public class ValueMapVersion extends VersionedEntity {
    private String valueMapSetId;
    private Integer versionNumber;
    private VersionLifecycleState lifecycleState;
    private Boolean caseSensitive;
    private UnmappedValuePolicy unmappedPolicy;
    private String defaultCanonicalValue;
    private String defaultExternalValue;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
