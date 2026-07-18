package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_form_field_definition")
public class LcFormFieldDefinition extends BaseEntity {
    private String tenantId;
    private String formDefinitionId;
    private String fieldKey;
    private String label;
    private String fieldType;
    private Integer requiredFlag;
    private String defaultValue;
    private String placeholder;
    private String optionsJson;
    private Integer sortOrder;
}
