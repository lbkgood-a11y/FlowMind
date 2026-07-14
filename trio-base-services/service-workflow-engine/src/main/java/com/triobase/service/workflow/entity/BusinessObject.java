package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_biz_object")
public class BusinessObject extends BaseEntity {
    private String tenantId;
    private String typeCode;
    private String displayName;
    private String serviceCode;
    private String description;
    private Integer version;
    private String status;
    private String sourceObjectId;
    private String metadataJson;
    private LocalDateTime publishedAt;
    private LocalDateTime offlineAt;
}
