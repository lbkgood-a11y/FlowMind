package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.entity.NotificationTaskEntity;
import com.triobase.service.ops.notification.mapper.NotificationTaskMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知任务的人工运营命令。
 *
 * <p>人工重试只恢复通知投递，不重放或回滚 Owner 业务事务。权限和租户范围均在更新前校验，
 * 状态条件更新保证并发操作不能复活已送达、取消或过期任务。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationTaskOperationService {

    public static final String OPERATE_PERMISSION = "OPS.MESSAGE.NOTIFICATION_OPERATE";

    private final NotificationTaskMapper taskMapper;
    private final RequestContextService contextService;
    private final NotificationWorkflowLauncher workflowLauncher;

    @Transactional
    public NotificationTaskEntity retry(String taskId) {
        if (!contextService.hasPermission(OPERATE_PERMISSION)) {
            throw new BizException(45410, "NOTIFICATION_OPERATION_FORBIDDEN");
        }
        String tenantId = contextService.tenantId();
        NotificationTaskEntity task = taskMapper.findOwned(tenantId, taskId);
        if (task == null) {
            throw new BizException(45400, "NOTIFICATION_TASK_NOT_FOUND");
        }
        if (taskMapper.resetForManualRetry(tenantId, taskId) != 1) {
            throw new BizException(45411, "NOTIFICATION_TASK_NOT_RETRYABLE");
        }
        task.setTaskState("ACCEPTED");
        task.setNextAttemptAt(null);
        workflowLauncher.launchAfterCommit(task);
        return task;
    }
}
