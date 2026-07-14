package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_object_permission")
public class BusinessObjectPermission extends BaseEntity {
    private String objectId;
    private String actionCode;
    private String displayName;
    private String permissionCode;
    private String actionGroup;
    private Integer sortOrder;
    private String metadataJson;
}
