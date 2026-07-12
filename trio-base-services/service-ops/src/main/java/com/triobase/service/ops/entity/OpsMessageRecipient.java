package com.triobase.service.ops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ops_message_recipient")
public class OpsMessageRecipient extends BaseEntity {
    private String tenantId;
    private String messageId;
    private String recipientUserId;
    private Short readStatus;
    private LocalDateTime readAt;
    private LocalDateTime deletedAt;
}
