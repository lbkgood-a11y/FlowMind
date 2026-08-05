package com.triobase.service.ops.notification.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.dto.notification.AuthorizedMembershipContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 从安全上下文提供当前用户的角色 ID（用于收件箱公告受众匹配）。
 *
 * <p>组织 ID 暂不从这里提供——组织成员解析需跨服务调用 service-org，
 * 后续通过独立的 Provider bean 或 Feign 远程解析实现。</p>
 */
@Component
public class SecurityContextMembershipProvider implements AuthorizedMembershipContextProvider {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextMembershipProvider.class);

    @Override
    public MembershipContext resolve(String tenantId, String userId) {
        List<String> roleIds = SecurityContextHolder.getRoleIds();
        if (roleIds == null) {
            roleIds = Collections.emptyList();
        }
        log.debug("Resolved membership context userId={} tenantId={} roleIds={}", userId, tenantId, roleIds);
        return new MembershipContext(Collections.emptyList(), roleIds);
    }
}
