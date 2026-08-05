package com.triobase.service.ops.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_notification_template_version")
public class NotificationTemplateVersionEntity {
    private String id;
    private String tenantId;
    private String templateId;
    private Integer versionNo;
    private String templateState;
    private String subjectTemplate;
    private String bodyTemplate;
    private String variableSchemaJson;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private String createdBy;
    private LocalDateTime createdAt;
}

