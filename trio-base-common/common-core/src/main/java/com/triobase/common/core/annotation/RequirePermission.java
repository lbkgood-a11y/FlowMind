package com.triobase.common.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级权限检查注解，标注在 Controller 方法上，
 * 由 PermissionAspect 拦截并校验当前用户是否拥有指定权限码。
 *
 * <p>权限码格式为 sys_permission 表中 resource || ':' || action，
 * 例如 "/api/v1/users:GET" 或 "System:Menu:Create"。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    String value();
}
