package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_object_status")
public class BusinessObjectStatus extends BaseEntity {
    private String objectId;
    private String statusCode;
    private String displayName;
    private String statusGroup;
    @TableField("is_initial")
    private Boolean initial;
    @TableField("is_terminal")
    private Boolean terminal;
    private Integer sortOrder;
    private String metadataJson;
}
