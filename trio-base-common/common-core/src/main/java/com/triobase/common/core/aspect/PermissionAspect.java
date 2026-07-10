package com.triobase.common.core.aspect;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Aspect
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                  RequirePermission requirePermission) throws Throwable {
        String required = requirePermission.value();
        List<String> permissions = SecurityContextHolder.getPermissions();

        if (permissions == null || !permissions.contains(required)) {
            log.warn("Permission denied: required={}, userPermissions={}", required, permissions);
            throw new BizException(AuthErrorCode.PERMISSION_DENIED);
        }

        return joinPoint.proceed();
    }
}
