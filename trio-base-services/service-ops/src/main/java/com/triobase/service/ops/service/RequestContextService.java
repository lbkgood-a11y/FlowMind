package com.triobase.service.ops.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RequestContextService {

    private static final String DEFAULT_TENANT = "default";

    public String tenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT;
    }

    public String userId() {
        String userId = SecurityContextHolder.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
        return userId;
    }

    public String username() {
        String username = SecurityContextHolder.getUsername();
        return StringUtils.hasText(username) ? username : userId();
    }

    public boolean hasPermission(String permissionCode) {
        return SecurityContextHolder.getPermissions().contains(permissionCode);
    }
}
