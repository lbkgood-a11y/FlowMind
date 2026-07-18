package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_application_version")
public class LcApplicationVersion extends BaseEntity {
    private String tenantId;
    private String applicationId;
    private String appKey;
    private Integer version;
    private String status;
    private String name;
    private String description;
    private String primaryFormDefinitionId;
    private String formKey;
    private Integer formVersion;
    private String schemaHash;
    private String viewPermissionCode;
    private String metadataHash;
    private LocalDateTime publishedAt;
    private LocalDateTime offlineAt;
    private String sourceApplicationVersionId;
}
