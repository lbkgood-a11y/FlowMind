package com.triobase.common.core.filter;

import com.triobase.common.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.triobase.common.core.context.SecurityContextHolder.SecurityContext;

@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuditSecurityFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_PERMISSIONS = "X-User-Permissions";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String userId = request.getHeader(HEADER_USER_ID);
            String username = request.getHeader(HEADER_USERNAME);
            String permissionsHeader = request.getHeader(HEADER_PERMISSIONS);

            if (userId != null && !userId.isBlank()) {
                List<String> permissions = permissionsHeader != null && !permissionsHeader.isBlank()
                        ? List.of(permissionsHeader.split(","))
                        : Collections.emptyList();

                SecurityContextHolder.set(new SecurityContext(userId, username, permissions));
            }

            filterChain.doFilter(request, response);

        } finally {
            SecurityContextHolder.clear();
        }
    }
}
