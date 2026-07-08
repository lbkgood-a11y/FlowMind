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

    public record SecurityContext(String userId, String username, List<String> permissions) {
        public SecurityContext {
            permissions = permissions != null ? List.copyOf(permissions) : Collections.emptyList();
        }
    }
}
