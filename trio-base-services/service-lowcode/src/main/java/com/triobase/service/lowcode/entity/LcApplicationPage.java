package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lc_application_page")
public class LcApplicationPage extends BaseEntity {
    private String tenantId;
    private String applicationVersionId;
    private String pageType;
    private String metadataJson;
    private Integer sortOrder;
}
