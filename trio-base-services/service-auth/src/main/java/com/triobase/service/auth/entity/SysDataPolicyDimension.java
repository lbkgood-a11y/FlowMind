package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_data_policy_dimension")
public class SysDataPolicyDimension extends BaseEntity {
    private String policyId;
    private String dimensionCode;
    private String scopeType;
    private String orgUnitIds;
    private Integer sortOrder;
}
