package com.triobase.common.core.aspect;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Aspect
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                  RequirePermission requirePermission) throws Throwable {
        String required = requirePermission.value();
        List<String> permissions = SecurityContextHolder.getPermissions();

        if (!hasPermission(permissions, required)) {
            log.warn("Permission denied: required={}, userPermissions={}", required, permissions);
            throw new BizException(AuthErrorCode.PERMISSION_DENIED);
        }

        return joinPoint.proceed();
    }

    private boolean hasPermission(List<String> permissions, String required) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(required);
        String requestPermissionCode = currentRequestPermissionCode();
        if (StringUtils.hasText(requestPermissionCode)) {
            candidates.add(requestPermissionCode);
        }
        return permissions.stream()
                .filter(StringUtils::hasText)
                .anyMatch(granted -> candidates.stream()
                        .anyMatch(candidate -> grantedMatches(granted, candidate)));
    }

    private String currentRequestPermissionCode() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        if (!StringUtils.hasText(request.getRequestURI()) || !StringUtils.hasText(request.getMethod())) {
            return null;
        }
        return request.getRequestURI() + ":" + request.getMethod();
    }

    private boolean grantedMatches(String granted, String required) {
        if (granted.equals(required)) {
            return true;
        }
        if (!granted.contains("*")) {
            return false;
        }
        String regex = Pattern.quote(granted)
                .replace("*", "\\E.*\\Q");
        return Pattern.compile(regex).matcher(required).matches();
    }
}
