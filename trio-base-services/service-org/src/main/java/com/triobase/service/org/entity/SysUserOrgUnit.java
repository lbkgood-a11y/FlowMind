package com.triobase.service.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_org_unit")
public class SysUserOrgUnit extends BaseEntity {
    private String tenantId;
    private String userId;
    private String dimensionId;
    private String orgUnitId;
    private Short isPrimary;
    private String positionId;
    private String positionName;
    private Short isLeader;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Short status;
}
