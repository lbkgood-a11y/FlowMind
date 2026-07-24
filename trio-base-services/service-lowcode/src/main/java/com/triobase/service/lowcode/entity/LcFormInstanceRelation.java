package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_form_instance_relation")
public class LcFormInstanceRelation extends BaseEntity {
    private String tenantId;
    private String applicationVersionId;
    private String relationCode;
    private String parentInstanceId;
    private String childInstanceId;
    private Integer sortOrder;
}
