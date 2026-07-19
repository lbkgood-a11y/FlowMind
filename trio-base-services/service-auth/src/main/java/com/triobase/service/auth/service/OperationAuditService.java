package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.auth.entity.SysOperationAuditLog;
import com.triobase.service.auth.mapper.OperationAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationAuditService {

    private static final String DEFAULT_TENANT = "default";

    private final OperationAuditLogMapper operationAuditLogMapper;

    public PageResult<SysOperationAuditLog> page(int page,
                                                 int size,
                                                 String username,
                                                 String userId,
                                                 String requestPath,
                                                 String resultStatus,
                                                 String actionId,
                                                 String actionType,
                                                 String actionSource,
                                                 String actionStatus,
                                                 String actionTargetType,
                                                 String actionTargetId,
                                                 String actionCorrelationId,
                                                 String actionIdempotencyKey,
                                                 LocalDateTime operatedStart,
                                                 LocalDateTime operatedEnd) {
        LambdaQueryWrapper<SysOperationAuditLog> wrapper = new LambdaQueryWrapper<SysOperationAuditLog>()
                .eq(SysOperationAuditLog::getTenantId, DEFAULT_TENANT)
                .like(StringUtils.hasText(username), SysOperationAuditLog::getUsername, username)
                .eq(StringUtils.hasText(userId), SysOperationAuditLog::getUserId, userId)
                .like(StringUtils.hasText(requestPath), SysOperationAuditLog::getRequestPath, requestPath)
                .eq(StringUtils.hasText(resultStatus), SysOperationAuditLog::getResultStatus, resultStatus)
                .eq(StringUtils.hasText(actionId), SysOperationAuditLog::getActionId, actionId)
                .eq(StringUtils.hasText(actionType), SysOperationAuditLog::getActionType, actionType)
                .eq(StringUtils.hasText(actionSource), SysOperationAuditLog::getActionSource, actionSource)
                .eq(StringUtils.hasText(actionStatus), SysOperationAuditLog::getActionStatus, actionStatus)
                .eq(StringUtils.hasText(actionTargetType), SysOperationAuditLog::getActionTargetType, actionTargetType)
                .eq(StringUtils.hasText(actionTargetId), SysOperationAuditLog::getActionTargetId, actionTargetId)
                .eq(StringUtils.hasText(actionCorrelationId), SysOperationAuditLog::getActionCorrelationId, actionCorrelationId)
                .eq(StringUtils.hasText(actionIdempotencyKey), SysOperationAuditLog::getActionIdempotencyKey,
                        actionIdempotencyKey)
                .ge(operatedStart != null, SysOperationAuditLog::getOperatedAt, operatedStart)
                .le(operatedEnd != null, SysOperationAuditLog::getOperatedAt, operatedEnd)
                .orderByDesc(SysOperationAuditLog::getOperatedAt);
        IPage<SysOperationAuditLog> result = operationAuditLogMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public SysOperationAuditLog detail(String id) {
        SysOperationAuditLog log = operationAuditLogMapper.selectById(id);
        if (log == null) {
            throw new BizException(40471, "AUDIT_LOG_NOT_FOUND");
        }
        return log;
    }

    public void record(SysOperationAuditLog log) {
        if (log == null || !StringUtils.hasText(log.getRequestPath())) {
            return;
        }
        log.setId(UlidGenerator.nextUlid());
        log.setTenantId(StringUtils.hasText(log.getTenantId()) ? log.getTenantId() : DEFAULT_TENANT);
        log.setResultStatus(StringUtils.hasText(log.getResultStatus()) ? log.getResultStatus() : "SUCCESS");
        log.setOperatedAt(log.getOperatedAt() != null ? log.getOperatedAt() : LocalDateTime.now());
        operationAuditLogMapper.insert(log);
    }
}
