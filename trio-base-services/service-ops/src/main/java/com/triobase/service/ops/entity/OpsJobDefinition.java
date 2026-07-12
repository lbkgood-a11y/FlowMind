package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_job_definition")
public class OpsJobDefinition extends BaseEntity {
    private String tenantId;
    private String jobCode;
    private String jobName;
    private String handlerName;
    private String cronExpression;
    private String jobParams;
    private Short enabled;
    private String description;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
}
