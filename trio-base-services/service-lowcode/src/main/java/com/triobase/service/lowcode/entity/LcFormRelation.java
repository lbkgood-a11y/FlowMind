package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_form_relation")
public class LcFormRelation extends BaseEntity {
    private String tenantId;
    private String applicationVersionId;
    private String relationCode;
    private String parentFormDefinitionId;
    private String childFormDefinitionId;
    private String cardinality;
    private String parentKeyField;
    private String childForeignKeyField;
    private Short cascadeSave;
    private Short cascadeDelete;
    private Integer sortOrder;
}
