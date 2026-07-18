package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_application")
public class LcApplication extends BaseEntity {
    private String tenantId;
    private String appKey;
    private String name;
    private String description;
    private String status;
    private Integer latestVersion;
    private String latestPublishedVersionId;
}
