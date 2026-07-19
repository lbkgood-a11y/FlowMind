package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_field")
public class SysAuthField extends BaseEntity {
    private String tenantId;
    private String resourceCode;
    private String fieldKey;
    private String fieldLabel;
    private String fieldType;
    private String sensitivityClassification;
    private String defaultMaskStrategy;
    private Short status;
}
