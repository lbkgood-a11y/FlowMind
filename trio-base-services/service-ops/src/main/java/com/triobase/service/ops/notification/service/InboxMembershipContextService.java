package com.triobase.service.ops.notification.service;

import com.triobase.common.dto.notification.AuthorizedMembershipContextProvider;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** 聚合已注册 Owner 的权限过滤成员上下文；任一解析失败时整体失败关闭为空集合。 */
@Service
public class InboxMembershipContextService {

    private final List<AuthorizedMembershipContextProvider> providers;

    public InboxMembershipContextService(Collection<AuthorizedMembershipContextProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public AuthorizedMembershipContextProvider.MembershipContext resolve(String tenantId, String userId) {
        LinkedHashSet<String> organizations = new LinkedHashSet<>();
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        try {
            for (AuthorizedMembershipContextProvider provider : providers) {
                var context = provider.resolve(tenantId, userId);
                if (context != null) {
                    addSafe(organizations, context.organizationIds());
                    addSafe(roles, context.roleIds());
                }
            }
            return new AuthorizedMembershipContextProvider.MembershipContext(
                    List.copyOf(organizations), List.copyOf(roles));
        } catch (RuntimeException resolutionFailure) {
            return new AuthorizedMembershipContextProvider.MembershipContext(List.of(), List.of());
        }
    }

    private void addSafe(LinkedHashSet<String> target, List<String> values) {
        if (values != null) {
            values.stream().filter(value -> value != null && !value.isBlank()).forEach(target::add);
        }
    }
}
