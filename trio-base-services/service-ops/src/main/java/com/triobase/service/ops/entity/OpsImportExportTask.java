package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_import_export_task")
public class OpsImportExportTask extends BaseEntity {
    private String tenantId;
    private String taskType;
    private String businessType;
    private String taskName;
    private String status;
    private Integer progress;
    private String requestParams;
    private String resultFileId;
    private String failureFileId;
    private Integer successCount;
    private Integer failureCount;
    private String failureReason;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
