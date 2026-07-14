package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_object_action")
public class BusinessObjectAction extends BaseEntity {
    private String objectId;
    private String actionCode;
    private String displayName;
    private String actionType;
    private String executorKey;
    private String modeDefault;
    private String permissionAction;
    private String paramSchemaJson;
    private Integer sortOrder;
    private String metadataJson;
}
