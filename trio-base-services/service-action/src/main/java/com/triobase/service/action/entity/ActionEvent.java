package com.triobase.service.action.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("act_action_event")
public class ActionEvent extends BaseEntity {
    private String actionId;
    private String tenantId;
    private String eventType;
    private String status;
    private Integer sequenceNo;
    private String message;
    private String eventDataJson;
    private String traceId;
    private LocalDateTime occurredAt;
}
