package com.triobase.common.core.filter;

import com.triobase.common.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_ROLES = "X-User-Roles";
    private static final String HEADER_PERMISSIONS = "X-User-Permissions";
    private static final String HEADER_AUTH_VERSION = "X-Auth-Version";
    private static final String HEADER_ROLE_VERSION = "X-Role-Version";
    private static final String HEADER_DATA_POLICY_VERSION = "X-Data-Policy-Version";
    private static final String HEADER_AUTHORIZATION_VERSION = "X-Authorization-Version";
    private static final String HEADER_FIELD_POLICY_VERSION = "X-Field-Policy-Version";
    private static final String HEADER_GUARD_TEMPLATE_VERSION = "X-Guard-Template-Version";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String userId = request.getHeader(HEADER_USER_ID);
            String username = request.getHeader(HEADER_USERNAME);
            String tenantId = request.getHeader(HEADER_TENANT_ID);
            String rolesHeader = request.getHeader(HEADER_ROLES);
            String permissionsHeader = request.getHeader(HEADER_PERMISSIONS);

            if (userId != null && !userId.isBlank()) {
                List<String> roles = rolesHeader != null && !rolesHeader.isBlank()
                        ? List.of(rolesHeader.split(","))
                        : Collections.emptyList();
                List<String> permissions = permissionsHeader != null && !permissionsHeader.isBlank()
                        ? List.of(permissionsHeader.split(","))
                        : Collections.emptyList();

                SecurityContextHolder.set(new SecurityContext(
                        userId,
                        username,
                        tenantId,
                        roles,
                        permissions,
                        parseLong(request.getHeader(HEADER_AUTH_VERSION)),
                        parseLong(request.getHeader(HEADER_ROLE_VERSION)),
                        parseLong(request.getHeader(HEADER_DATA_POLICY_VERSION)),
                        parseLong(request.getHeader(HEADER_AUTHORIZATION_VERSION)),
                        parseLong(request.getHeader(HEADER_FIELD_POLICY_VERSION)),
                        parseLong(request.getHeader(HEADER_GUARD_TEMPLATE_VERSION))
                ));
            }

            filterChain.doFilter(request, response);

        } finally {
            SecurityContextHolder.clear();
        }
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
