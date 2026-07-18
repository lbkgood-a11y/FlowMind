package com.triobase.service.openapi.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.integration.IntegrationAdmissionDecision;
import com.triobase.common.dto.integration.IntegrationAdmissionRequest;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.RuntimeAdmissionContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RuntimeAdmissionContextResolver {

    private static final long DEFAULT_MAX_CONCURRENCY = 100L;
    private static final long DEFAULT_MAX_ACTIVE_WORKFLOWS = 20L;

    private final IntegrationAdmissionService admissionService;
    private final GatewayTrustVerifier gatewayTrustVerifier;

    public RuntimeAdmissionContextResolver(
            IntegrationAdmissionService admissionService,
            GatewayTrustVerifier gatewayTrustVerifier) {
        this.admissionService = admissionService;
        this.gatewayTrustVerifier = gatewayTrustVerifier;
    }

    public RuntimeAdmissionContext resolve(
            HttpServletRequest request,
            String routeKey,
            Environment environment,
            String operation) {
        if (trustedGateway(request)) {
            return fromGatewayHeaders(request, environment);
        }
        IntegrationAdmissionDecision decision = admissionService.admit(new IntegrationAdmissionRequest(
                header(request, "X-Tenant-Id"),
                header(request, "X-Client-Key"),
                environmentName(request, environment),
                routeKey,
                operation,
                credential(request),
                sourceIp(request),
                Math.max(0L, request.getContentLengthLong()),
                tls(request),
                scopes(request),
                header(request, "X-Signature-Timestamp"),
                header(request, "X-Signature-Nonce"),
                header(request, "X-Signature"),
                header(request, "X-Content-SHA256"),
                header(request, "X-Client-Certificate-Fingerprint"),
                false));
        if (decision == null || !decision.allowed()) {
            throw admissionDenied(decision);
        }
        return new RuntimeAdmissionContext(
                decision.tenantId(),
                environment,
                decision.applicationClientId(),
                decision.subscriptionId(),
                decision.policyVersion(),
                positiveOrDefault(decision.maxConcurrency(), DEFAULT_MAX_CONCURRENCY),
                positiveOrDefault(decision.maxActiveWorkflows(), DEFAULT_MAX_ACTIVE_WORKFLOWS));
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

    private String credential(HttpServletRequest request) {
        String credential = header(request, "X-Client-Credential");
        return StringUtils.hasText(credential) ? credential : header(request, HttpHeaders.AUTHORIZATION);
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = header(request, "X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private boolean tls(HttpServletRequest request) {
        String forwardedProto = header(request, "X-Forwarded-Proto");
        return request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
    }

    private Set<String> scopes(HttpServletRequest request) {
        String value = header(request, "X-Scopes");
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split("[, ]+"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private BizException admissionDenied(IntegrationAdmissionDecision decision) {
        if (decision == null) {
            return new BizException(50390, "OPENAPI_RUNTIME_ADMISSION_UNAVAILABLE");
        }
        int code = switch (decision.httpStatus()) {
            case 401 -> 40130;
            case 403 -> 40380;
            case 413 -> 41301;
            case 429 -> 42901;
            default -> decision.httpStatus() >= 500 ? 50390 : 40380;
        };
        return new BizException(code, decision.reason());
    }

    private long positiveOrDefault(long value, long fallback) {
        return value > 0 ? value : fallback;
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
