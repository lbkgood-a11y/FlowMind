package com.triobase.common.core.aspect;

import com.triobase.common.core.annotation.RequireDataScope;
import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.auth.DataScopeProvider;
import com.triobase.common.core.context.DataScopeContextHolder;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

@Aspect
public class DataScopeAspect {

    private final ObjectProvider<DataScopeProvider> dataScopeProvider;

    public DataScopeAspect(ObjectProvider<DataScopeProvider> dataScopeProvider) {
        this.dataScopeProvider = dataScopeProvider;
    }

    @Around("@annotation(requireDataScope)")
    public Object resolveDataScope(ProceedingJoinPoint joinPoint,
                                   RequireDataScope requireDataScope) throws Throwable {
        String userId = SecurityContextHolder.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BizException(AuthErrorCode.PERMISSION_DENIED);
        }

        String resourceCode = requireDataScope.resource();
        String actionCode = requireDataScope.action();
        DataScopeProvider provider = dataScopeProvider.getIfAvailable();
        DataScope dataScope = provider != null
                ? provider.resolve(userId, resourceCode, actionCode)
                : DataScope.restrictive(userId, resourceCode, actionCode);

        DataScopeContextHolder.set(dataScope != null
                ? dataScope
                : DataScope.restrictive(userId, resourceCode, actionCode));
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContextHolder.clear();
        }
    }
}
