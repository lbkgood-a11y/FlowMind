package com.triobase.service.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_auth_page_catalog")
public class SysAuthPageCatalog extends BaseEntity {
    private String tenantId;
    private String catalogCode;
    private Long catalogVersion;
    private String sourceType;
    private String sourceRef;
    private String manifestHash;
    private String lifecycleStatus;
    private LocalDateTime activatedAt;
}
