package com.triobase.service.apiruntime.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.openapi.enums.Environment;
import com.triobase.common.openapi.dto.RuntimeAdmissionContext;
import com.triobase.common.openapi.integration.GatewayTrustVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class RuntimeAdmissionContextResolver {

    private static final long DEFAULT_MAX_CONCURRENCY = 100L;
    private static final long DEFAULT_MAX_ACTIVE_WORKFLOWS = 20L;

    private final GatewayTrustVerifier gatewayTrustVerifier;

    public RuntimeAdmissionContextResolver(GatewayTrustVerifier gatewayTrustVerifier) {
        this.gatewayTrustVerifier = gatewayTrustVerifier;
    }

    public RuntimeAdmissionContext resolve(
            HttpServletRequest request,
            String routeKey,
            Environment environment,
            String operation) {
        if (!trustedGateway(request)) {
            throw new BizException(40130, "OPENAPI_RUNTIME_GATEWAY_CONTEXT_REQUIRED");
        }
        return fromGatewayHeaders(request, environment);
    }

    private RuntimeAdmissionContext fromGatewayHeaders(HttpServletRequest request, Environment fallbackEnvironment) {
        String tenantId = header(request, "X-Tenant-Id");
        String clientId = header(request, "X-Application-Client-Id");
        String subscriptionId = header(request, "X-Subscription-Id");
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(clientId)
                || !StringUtils.hasText(subscriptionId)) {
            throw new BizException(40130, "OPENAPI_RUNTIME_ADMISSION_CONTEXT_REQUIRED");
        }
        Environment environment = Environment.valueOf(environmentName(request, fallbackEnvironment));
        return new RuntimeAdmissionContext(
                tenantId,
                environment,
                clientId,
                subscriptionId,
                parseLong(header(request, "X-Policy-Version"), 0L),
                parseLong(header(request, "X-Max-Concurrency"), DEFAULT_MAX_CONCURRENCY),
                parseLong(header(request, "X-Max-Active-Workflows"), DEFAULT_MAX_ACTIVE_WORKFLOWS));
    }

    private boolean trustedGateway(HttpServletRequest request) {
        return gatewayTrustVerifier.trusted(request);
    }

    private String environmentName(HttpServletRequest request, Environment fallback) {
        String value = header(request, "X-Environment");
        return StringUtils.hasText(value) ? value.toUpperCase(Locale.ROOT) : fallback.name();
    }

    private long parseLong(String value, long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String header(HttpServletRequest request, String name) {
        return request.getHeader(name);
    }
}
