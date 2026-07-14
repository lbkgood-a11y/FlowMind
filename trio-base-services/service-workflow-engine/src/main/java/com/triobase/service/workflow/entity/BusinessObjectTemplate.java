package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_object_template")
public class BusinessObjectTemplate extends BaseEntity {
    private String objectId;
    private String templateCode;
    private String displayName;
    private String templateType;
    private String configJson;
    private Integer sortOrder;
}
