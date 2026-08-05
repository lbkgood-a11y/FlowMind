package com.triobase.common.dto.notification;

import java.util.List;

/**
 * 由组织、授权或业务 Owner 提供的权限过滤受众解析契约。
 *
 * <p>实现必须验证 {@code tenantId}、调用者数据范围和选择器注册状态。任何解析缺失或失败都
 * 必须失败关闭，禁止返回租户全量用户作为降级结果。</p>
 */
public interface AuthorizedAudienceResolver {

    String resolverKey();

    String resolverVersion();

    AudienceResolutionPage resolve(String tenantId,
                                   String actorId,
                                   AudienceSelector selector,
                                   String cursor,
                                   int limit);

    record AudienceResolutionPage(List<String> authorizedUserIds, String nextCursor) {
    }
}
