package com.triobase.service.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org_dimension")
public class SysOrgDimension extends BaseEntity {
    private String tenantId;
    private String dimensionCode;
    private String dimensionName;
    private Short isDefault;
    private Short status;
    private Integer sortOrder;
    private String description;
}
