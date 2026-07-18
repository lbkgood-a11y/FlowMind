package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_application_action")
public class LcApplicationAction extends BaseEntity {
    private String tenantId;
    private String applicationVersionId;
    private String actionCode;
    private String actionType;
    private String label;
    private String permissionCode;
    private String formDefinitionId;
    private String processKey;
    private String metadataJson;
    private String status;
    private Integer sortOrder;
}
