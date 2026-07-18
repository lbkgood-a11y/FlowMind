package com.triobase.service.openapi.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class GatewayTrustVerifier {

    static final String GATEWAY_AUTHENTICATED_HEADER = "X-Gateway-Authenticated";
    static final String GATEWAY_SECRET_HEADER = "X-OpenAPI-Gateway-Secret";

    private final String gatewayAuthSecret;

    public GatewayTrustVerifier(
            @Value("${triobase.openapi.gateway-auth-secret:}") String gatewayAuthSecret) {
        this.gatewayAuthSecret = gatewayAuthSecret;
    }

    public boolean trusted(HttpServletRequest request) {
        return trusted(
                request.getHeader(GATEWAY_AUTHENTICATED_HEADER),
                request.getHeader(GATEWAY_SECRET_HEADER));
    }

    boolean trusted(String authenticated, String providedSecret) {
        if (!"true".equalsIgnoreCase(authenticated)
                || !StringUtils.hasText(gatewayAuthSecret)
                || !StringUtils.hasText(providedSecret)) {
            return false;
        }
        return MessageDigest.isEqual(
                gatewayAuthSecret.getBytes(StandardCharsets.UTF_8),
                providedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
