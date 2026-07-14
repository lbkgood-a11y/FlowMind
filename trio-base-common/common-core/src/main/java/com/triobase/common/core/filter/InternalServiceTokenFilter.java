package com.triobase.common.core.filter;

import com.triobase.common.core.config.InternalServiceSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_SERVICE_NAME = "X-Internal-Service";
    public static final String HEADER_SERVICE_TOKEN = "X-Internal-Token";

    private final InternalServiceSecurityProperties properties;

    public InternalServiceTokenFilter(InternalServiceSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith("/internal/v1/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String caller = request.getHeader(HEADER_SERVICE_NAME);
        String token = request.getHeader(HEADER_SERVICE_TOKEN);
        boolean callerAllowed = caller != null && properties.getAllowedCallers().contains(caller);
        boolean tokenValid = secureEquals(properties.getToken(), token);
        if (!callerAllowed || !tokenValid) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_INTERNAL_SERVICE_CREDENTIALS");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
