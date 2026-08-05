package com.triobase.service.ops.notification.service;

import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.dto.notification.BusinessResourceReference;

/**
 * 启动时注册的 Owner 集成边界。
 *
 * <p>实现必须调用固定 Owner API 并执行实时授权；禁止根据消息字段拼接 URL。导航结果只返回
 * 前端注册表键，不返回任意外链。动作必须进入 Owner-hosted Global Action runtime。</p>
 */
public interface InboxOwnerIntegration {

    String ownerService();

    RegisteredNavigation authorizeNavigation(BusinessResourceReference reference,
                                             String tenantId, String userId);

    GlobalActionResult dispatch(GlobalActionRequest request);

    record RegisteredNavigation(String applicationKey, String resourceKey,
                                String resourceId, boolean available) {
    }
}
