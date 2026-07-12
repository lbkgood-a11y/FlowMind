package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_file_reference")
public class OpsFileReference extends BaseEntity {
    private String tenantId;
    private String fileId;
    private String businessType;
    private String businessId;
    private String refType;
}
