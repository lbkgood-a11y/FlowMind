package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.dto.NotificationOperationalDiagnostic;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import com.triobase.service.ops.notification.mapper.NotificationDeliveryAttemptMapper;
import com.triobase.service.ops.notification.mapper.NotificationTaskMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 仅向通知运营权限返回租户内脱敏诊断；不得扩展为收件人证据查询。 */
@Service
@RequiredArgsConstructor
public class NotificationOperationalDiagnosticService {

    private final NotificationTaskMapper taskMapper;
    private final NotificationDeliveryAttemptMapper attemptMapper;
    private final RequestContextService contextService;

    public NotificationOperationalDiagnostic find(String taskId) {
        if (!contextService.hasPermission(NotificationTaskOperationService.OPERATE_PERMISSION)) {
            throw new BizException(45410, "NOTIFICATION_OPERATION_FORBIDDEN");
        }
        String tenantId = contextService.tenantId();
        NotificationTaskEntity task = taskMapper.findOwned(tenantId, taskId);
        if (task == null) {
            throw new BizException(45400, "NOTIFICATION_TASK_NOT_FOUND");
        }
        var attempts = attemptMapper.findRecent(tenantId, taskId, 50).stream()
                .map(item -> new NotificationOperationalDiagnostic.Attempt(
                        item.getDeliveryStatus(), Short.valueOf((short) 1).equals(item.getRetryable()),
                        NotificationSafeText.classification(item.getErrorCategory()),
                        NotificationSafeText.summary(item.getSanitizedMessage()), item.getOccurredAt()))
                .toList();
        return new NotificationOperationalDiagnostic(taskId, task.getTaskState(), task.getEventId(),
                task.getTraceId(), attempts);
    }
}
