package com.triobase.service.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org_unit")
public class SysOrgUnit extends BaseEntity {
    private String tenantId;
    private String parentId;
    private String unitCode;
    private String unitName;
    private String unitType;
    private String treePath;
    private Integer sortOrder;
    private Short status;
    private String description;
}
