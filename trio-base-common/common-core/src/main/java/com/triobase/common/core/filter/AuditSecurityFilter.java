package com.triobase.common.core.filter;

import com.triobase.common.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.triobase.common.core.context.SecurityContextHolder.SecurityContext;

@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuditSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditSecurityFilter.class);

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_ROLES = "X-User-Roles";
    private static final String HEADER_ROLE_IDS = "X-User-Role-Ids";
    private static final String HEADER_PERMISSIONS = "X-User-Permissions";
    private static final String HEADER_DENIED_PERMISSIONS = "X-User-Denied-Permissions";
    private static final String HEADER_AUTH_VERSION = "X-Auth-Version";
    private static final String HEADER_ROLE_VERSION = "X-Role-Version";
    private static final String HEADER_DATA_POLICY_VERSION = "X-Data-Policy-Version";
    private static final String HEADER_AUTHORIZATION_VERSION = "X-Authorization-Version";
    private static final String HEADER_FIELD_POLICY_VERSION = "X-Field-Policy-Version";
    private static final String HEADER_GUARD_TEMPLATE_VERSION = "X-Guard-Template-Version";

    private static final List<String> TRUSTED_HEADERS = List.of(
            HEADER_USER_ID, HEADER_USERNAME, HEADER_TENANT_ID,
            HEADER_ROLES, HEADER_PERMISSIONS, HEADER_DENIED_PERMISSIONS,
            HEADER_AUTH_VERSION, HEADER_ROLE_VERSION, HEADER_DATA_POLICY_VERSION,
            HEADER_AUTHORIZATION_VERSION, HEADER_FIELD_POLICY_VERSION,
            HEADER_GUARD_TEMPLATE_VERSION);

    private final String expectedGatewaySecret;

    public AuditSecurityFilter(String expectedGatewaySecret) {
        this.expectedGatewaySecret = expectedGatewaySecret;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            boolean fromGateway = isFromGateway(request);

            String userId = request.getHeader(HEADER_USER_ID);
            String username = request.getHeader(HEADER_USERNAME);
            String tenantId = request.getHeader(HEADER_TENANT_ID);
            String rolesHeader = request.getHeader(HEADER_ROLES);
            String roleIdsHeader = request.getHeader(HEADER_ROLE_IDS);
            String permissionsHeader = request.getHeader(HEADER_PERMISSIONS);
            String deniedPermissionsHeader = request.getHeader(HEADER_DENIED_PERMISSIONS);

            if (userId != null && !userId.isBlank()) {
                if (!fromGateway) {
                    log.warn("Identity headers present without gateway validation — REJECTED. "
                            + "uri={} ip={} userId={}",
                            request.getRequestURI(), request.getRemoteAddr(), userId);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                            "IDENTITY_HEADERS_REQUIRE_GATEWAY");
                    return;
                }
                List<String> roles = rolesHeader != null && !rolesHeader.isBlank()
                        ? List.of(rolesHeader.split(","))
                        : Collections.emptyList();
                List<String> roleIds = roleIdsHeader != null && !roleIdsHeader.isBlank()
                        ? List.of(roleIdsHeader.split(","))
                        : Collections.emptyList();
                List<String> permissions = permissionsHeader != null && !permissionsHeader.isBlank()
                        ? List.of(permissionsHeader.split(","))
                        : Collections.emptyList();
                List<String> deniedPermissions = deniedPermissionsHeader != null && !deniedPermissionsHeader.isBlank()
                        ? List.of(deniedPermissionsHeader.split(","))
                        : Collections.emptyList();

                SecurityContextHolder.set(new SecurityContext(
                        userId,
                        username,
                        tenantId,
                        roles,
                        permissions,
                        deniedPermissions,
                        parseLong(request.getHeader(HEADER_AUTH_VERSION)),
                        parseLong(request.getHeader(HEADER_ROLE_VERSION)),
                        parseLong(request.getHeader(HEADER_DATA_POLICY_VERSION)),
                        parseLong(request.getHeader(HEADER_AUTHORIZATION_VERSION)),
                        parseLong(request.getHeader(HEADER_FIELD_POLICY_VERSION)),
                        parseLong(request.getHeader(HEADER_GUARD_TEMPLATE_VERSION))
                ));
                SecurityContextHolder.setRoleIds(roleIds);
            }

            filterChain.doFilter(request, response);

        } finally {
            SecurityContextHolder.clear();
        }
    }

    private boolean isFromGateway(HttpServletRequest request) {
        String serviceName = request.getHeader("X-Internal-Service");
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        String token = request.getHeader("X-Internal-Token");
        if (expectedGatewaySecret != null && !expectedGatewaySecret.isBlank()
                && token != null && !token.isBlank()) {
            return java.security.MessageDigest.isEqual(
                    expectedGatewaySecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return false;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
