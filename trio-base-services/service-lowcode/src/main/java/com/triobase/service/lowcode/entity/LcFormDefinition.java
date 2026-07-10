package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_form_definition")
public class LcFormDefinition extends BaseEntity {
    private String formKey;
    private String name;
    private String description;
    private Integer version;
    private String status;
    private String schemaJson;
    private String uiSchemaJson;
}
