package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 高敏通知操作的追加式安全审计，仅允许保存分类化元数据。 */
@Data
@TableName("ops_notification_security_audit")
public class NotificationSecurityAuditEntity {
    @TableId
    private String id;
    private String tenantId;
    private String actorUserId;
    private String actionCode;
    private String resourceType;
    private String resourceId;
    private String safeDetails;
    private String traceId;
    private LocalDateTime occurredAt;
}

