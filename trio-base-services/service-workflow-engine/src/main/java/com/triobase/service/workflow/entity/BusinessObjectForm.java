package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_object_form")
public class BusinessObjectForm extends BaseEntity {
    private String objectId;
    private String formRole;
    private String displayName;
    private String formDefinitionId;
    private String formKey;
    private Integer formVersion;
    private Boolean required;
    private Integer sortOrder;
    private String metadataJson;
}
