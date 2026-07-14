package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_object_event")
public class BusinessObjectEvent extends BaseEntity {
    private String objectId;
    private String eventCode;
    private String displayName;
    private String eventType;
    private String payloadSchemaJson;
    private Integer sortOrder;
    private String metadataJson;
}
