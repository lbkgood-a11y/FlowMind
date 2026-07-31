package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_auth_intent")
public class SysRoleAuthIntent extends BaseEntity {
    private String tenantId;
    private String draftId;
    private String capabilityId;
    private String selectionSource;
    private String defaultScopeType;
    private String defaultScopeIds;
    private String operationScopeType;
    private String operationScopeIds;
    private String fieldIntentJson;
    private String constraintIntentJson;
}
