package com.triobase.service.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_org_unit")
public class SysUserOrgUnit extends BaseEntity {
    private String userId;
    private String orgUnitId;
}
