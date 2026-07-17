package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.ResolveCallbackQuarantineRequest;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CallbackQuarantineService {
    private final CallbackInboxMapper inboxMapper;
    private final IntegrationExecutionMapper executionMapper;
    private final IntegrationAuditService auditService;

    public List<CallbackInbox> list(int limit) {
        String tenantId = SecurityContextHolder.getTenantId();
        return inboxMapper.selectList(new LambdaQueryWrapper<CallbackInbox>()
                .eq(tenantId != null, CallbackInbox::getTenantId, tenantId)
                .eq(CallbackInbox::getInboxState, CallbackInboxState.QUARANTINED)
                .orderByDesc(CallbackInbox::getReceivedAt)
                .last("LIMIT " + Math.min(200, Math.max(1, limit))));
    }

    @Transactional
    public CallbackInbox resolve(String inboxId, ResolveCallbackQuarantineRequest request) {
        CallbackInbox inbox = requireInbox(inboxId);
        if (inbox.getInboxState() != CallbackInboxState.QUARANTINED) {
            throw new BizException(40972, "OPENAPI_CALLBACK_NOT_QUARANTINED");
        }
        String action = request.action().toUpperCase(Locale.ROOT);
        switch (action) {
            case "DISCARD" -> inbox.setInboxState(CallbackInboxState.FAILED);
            case "RETRY", "LINK" -> {
                String executionId = request.executionId() == null
                        ? inbox.getExecutionId() : request.executionId();
                IntegrationExecution execution = executionMapper.selectById(executionId);
                if (execution == null || !inbox.getTenantId().equals(execution.getTenantId())
                        || !inbox.getApplicationClientId().equals(execution.getApplicationClientId())
                        || execution.getExecutionState() != ExecutionState.WAITING_CALLBACK) {
                    throw new BizException(40972, "OPENAPI_CALLBACK_RESOLUTION_EXECUTION_INVALID");
                }
                inbox.setExecutionId(executionId);
                inbox.setInboxState(CallbackInboxState.SIGNAL_PENDING);
                inbox.setNextSignalAt(LocalDateTime.now());
            }
            default -> throw new BizException(40072, "OPENAPI_CALLBACK_RESOLUTION_ACTION_INVALID");
        }
        inbox.setResolutionState(action);
        inbox.setResolutionNote(request.note());
        inbox.setResolvedBy(operator());
        inbox.setResolvedAt(LocalDateTime.now());
        inbox.setUpdatedAt(LocalDateTime.now());
        inboxMapper.updateById(inbox);
        auditService.success("CALLBACK_QUARANTINE_RESOLVED", "CALLBACK_INBOX", inboxId,
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("action", action));
        return inbox;
    }

    private CallbackInbox requireInbox(String id) {
        CallbackInbox inbox = inboxMapper.selectById(id);
        String tenantId = SecurityContextHolder.getTenantId();
        if (inbox == null || (tenantId != null && !tenantId.equals(inbox.getTenantId()))) {
            throw new BizException(40472, "OPENAPI_CALLBACK_INBOX_NOT_FOUND");
        }
        return inbox;
    }

    private String operator() {
        String user = SecurityContextHolder.getUserId();
        return user == null || user.isBlank() ? "SYSTEM" : user;
    }
}
