package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_action")
public class SysAuthAction extends BaseEntity {
    private String tenantId;
    private String resourceCode;
    private String actionCode;
    private String actionCategory;
    private String description;
    private String guardCodes;
    private Short status;
}
