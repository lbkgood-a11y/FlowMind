package com.triobase.common.dto.notification;

import java.util.List;

/**
 * Owner 提供的当前用户成员上下文。
 *
 * <p>返回的组织与角色必须已经按 tenantId、userId 和当前授权范围过滤；调用方只能用于收窄
 * 可见性，解析失败必须返回空集合或抛错，禁止降级为租户全量成员。</p>
 */
public interface AuthorizedMembershipContextProvider {

    MembershipContext resolve(String tenantId, String userId);

    record MembershipContext(List<String> organizationIds, List<String> roleIds) {
    }
}
