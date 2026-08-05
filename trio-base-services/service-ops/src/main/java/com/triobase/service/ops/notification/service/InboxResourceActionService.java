package com.triobase.service.ops.notification.service;

import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionExecutionMode;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.common.dto.notification.BusinessResourceReference;
import com.triobase.service.ops.notification.entity.InboxProjectionEntity;
import com.triobase.service.ops.notification.mapper.InboxProjectionMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 消息资源导航和副作用执行边界；消息已读状态不参与 Owner 业务状态判断。 */
@Service
@RequiredArgsConstructor
public class InboxResourceActionService {

    private final InboxProjectionMapper projectionMapper;
    private final InboxOwnerIntegrationRegistry registry;
    private final RequestContextService contextService;

    public InboxOwnerIntegration.RegisteredNavigation navigation(String itemId) {
        InboxProjectionEntity item = requireOwned(itemId);
        BusinessResourceReference reference = reference(item, false);
        return registry.require(reference.ownerService()).authorizeNavigation(
                reference, item.getTenantId(), contextService.userId());
    }

    public GlobalActionResult execute(String itemId, String idempotencyKey,
                                      Map<String, Object> payload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(45511, "INBOX_ACTION_IDEMPOTENCY_REQUIRED");
        }
        InboxProjectionEntity item = requireOwned(itemId);
        BusinessResourceReference reference = reference(item, true);
        InboxOwnerIntegration integration = registry.require(reference.ownerService());
        // 动作前再次解析导航权限，防止消息创建后权限回收仍可执行副作用。
        var navigation = integration.authorizeNavigation(
                reference, item.getTenantId(), contextService.userId());
        if (navigation == null || !navigation.available()) {
            throw new BizException(45512, "INBOX_RESOURCE_UNAVAILABLE");
        }
        return integration.dispatch(action(reference, idempotencyKey, payload));
    }

    private InboxProjectionEntity requireOwned(String itemId) {
        InboxProjectionEntity item = projectionMapper.findOwned(
                contextService.tenantId(), contextService.userId(), itemId);
        if (item == null || item.getHiddenAt() != null) {
            throw new BizException(45501, "INBOX_ITEM_NOT_FOUND");
        }
        return item;
    }

    private BusinessResourceReference reference(InboxProjectionEntity item, boolean requireAction) {
        if (item.getSourceOwner() == null || item.getResourceKey() == null
                || (requireAction && item.getActionId() == null)) {
            throw new BizException(45513, "INBOX_REGISTERED_REFERENCE_REQUIRED");
        }
        return new BusinessResourceReference(item.getSourceOwner(), item.getResourceType(),
                item.getResourceId(), item.getResourceKey(), item.getActionId());
    }

    private GlobalActionRequest action(BusinessResourceReference reference, String idempotencyKey,
                                       Map<String, Object> payload) {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionId(reference.actionId());
        request.setActionType(reference.actionId());
        request.setSource(ActionSource.GUI);
        request.setExecutionMode(ActionExecutionMode.SYNC);
        request.setIdempotencyKey(idempotencyKey);
        request.setPayload(payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload));
        request.getActor().setType(ActionActorType.USER);
        request.getActor().setId(contextService.userId());
        request.getActor().setTenantId(contextService.tenantId());
        request.getTarget().setType(reference.resourceType());
        request.getTarget().setId(reference.resourceId());
        request.getTarget().setOwnerService(reference.ownerService());
        request.getTarget().setTenantId(contextService.tenantId());
        request.getContext().setTenantId(contextService.tenantId());
        request.getContext().setTraceId(TraceUtil.getTraceId());
        request.getContext().setRequestId(idempotencyKey);
        request.getContext().setCorrelationId(reference.resourceId());
        return request;
    }
}
