package com.triobase.common.core.context;

import java.util.Collections;
import java.util.List;

/**
 * 请求级安全上下文持有器 — 由网关 JwtAuthFilter 注入，业务代码通过 get* 方法读取。
 * 基於 ThreadLocal，请求结束后由 Filter 自动清理，防止内存泄漏。
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<SecurityContext> CONTEXT = new ThreadLocal<>();

    private SecurityContextHolder() {
    }

    public static void set(SecurityContext context) {
        CONTEXT.set(context);
    }

    public static SecurityContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static String getUserId() {
        SecurityContext ctx = CONTEXT.get();
        return ctx != null ? ctx.userId() : null;
    }

    public static String getUsername() {
        SecurityContext ctx = CONTEXT.get();
        return ctx != null ? ctx.username() : null;
    }

    public static List<String> getPermissions() {
        SecurityContext ctx = CONTEXT.get();
        return ctx != null ? ctx.permissions() : Collections.emptyList();
    }

    public static String getTenantId() {
        SecurityContext ctx = CONTEXT.get();
        return ctx != null ? ctx.tenantId() : null;
    }

    public static List<String> getRoles() {
        SecurityContext ctx = CONTEXT.get();
        return ctx != null ? ctx.roles() : Collections.emptyList();
    }

    public record SecurityContext(String userId,
                                  String username,
                                  String tenantId,
                                  List<String> roles,
                                  List<String> permissions,
                                  Long authVersion,
                                  Long roleVersion,
                                  Long dataPolicyVersion,
                                  Long authorizationVersion,
                                  Long fieldPolicyVersion,
                                  Long guardTemplateVersion) {
        public SecurityContext(String userId, String username, List<String> permissions) {
            this(userId, username, null, Collections.emptyList(), permissions, null, null, null);
        }

        public SecurityContext(String userId,
                               String username,
                               String tenantId,
                               List<String> roles,
                               List<String> permissions,
                               Long authVersion,
                               Long roleVersion,
                               Long dataPolicyVersion) {
            this(userId, username, tenantId, roles, permissions,
                    authVersion, roleVersion, dataPolicyVersion, null, null, null);
        }

        public SecurityContext {
            roles = roles != null ? List.copyOf(roles) : Collections.emptyList();
            permissions = permissions != null ? List.copyOf(permissions) : Collections.emptyList();
        }
    }
}
