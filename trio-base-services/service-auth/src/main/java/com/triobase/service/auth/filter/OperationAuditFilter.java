package com.triobase.service.auth.filter;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.auth.entity.SysOperationAuditLog;
import com.triobase.service.auth.service.OperationAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class OperationAuditFilter extends OncePerRequestFilter {

    private final OperationAuditService operationAuditService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        Throwable thrown = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            thrown = ex;
            throw ex;
        } finally {
            recordAudit(request, response, start, thrown);
        }
    }

    private void recordAudit(HttpServletRequest request,
                             HttpServletResponse response,
                             long start,
                             Throwable thrown) {
        if (!shouldAudit(request)) {
            return;
        }
        try {
            String userId = SecurityContextHolder.getUserId();
            if (!StringUtils.hasText(userId)) {
                return;
            }
            String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String path = StringUtils.hasText(pattern) ? pattern : request.getRequestURI();
            SysOperationAuditLog audit = new SysOperationAuditLog();
            audit.setTenantId(StringUtils.hasText(SecurityContextHolder.getTenantId()) ? SecurityContextHolder.getTenantId() : "default");
            audit.setUserId(userId);
            audit.setUsername(SecurityContextHolder.getUsername());
            audit.setPermissionCode(path + ":" + request.getMethod());
            audit.setModuleName(resolveModule(path));
            audit.setActionName(request.getMethod());
            audit.setHttpMethod(request.getMethod());
            audit.setRequestPath(path);
            audit.setQueryString(limit(request.getQueryString(), 2000));
            audit.setClientIp(clientIp(request));
            audit.setUserAgent(limit(request.getHeader("User-Agent"), 512));
            audit.setRequestSummary(limit(request.getMethod() + " " + request.getRequestURI(), 1000));
            audit.setResultStatus(thrown == null && response.getStatus() < 400 ? "SUCCESS" : "FAILURE");
            audit.setStatusCode(response.getStatus());
            audit.setErrorMessage(thrown != null ? limit(thrown.getMessage(), 512) : null);
            audit.setLatencyMs(System.currentTimeMillis() - start);
            audit.setTraceId(traceId(request));
            audit.setOperatedAt(LocalDateTime.now());
            operationAuditService.record(audit);
        } catch (Exception ex) {
            log.warn("Failed to record operation audit: {}", ex.getMessage());
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return false;
        }
        return !path.startsWith("/api/v1/auth/")
                && !path.startsWith("/api/v1/audit-logs")
                && !path.startsWith("/actuator")
                && !"OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private String resolveModule(String path) {
        if (!StringUtils.hasText(path)) {
            return "unknown";
        }
        String normalized = path.replace("/api/v1/", "");
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(0, slash) : normalized;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp : request.getRemoteAddr();
    }

    private String traceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-B3-TraceId");
        return StringUtils.hasText(traceId) ? traceId : request.getHeader("traceparent");
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
