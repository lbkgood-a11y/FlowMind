package com.triobase.service.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org_relation")
public class SysOrgRelation extends BaseEntity {
    private String tenantId;
    private String dimensionId;
    private String parentUnitId;
    private String childUnitId;
    private String treePath;
    private Integer level;
    private Integer sortOrder;
    private Short status;
}
