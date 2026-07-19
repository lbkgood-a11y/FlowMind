package com.triobase.platform.gateway.filter;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.auth.TokenValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 鉴权全局过滤器 — 铁律 8（TraceId 透传）。
 * 从请求头提取 Bearer Token → 调用 service-auth /validate → 注入用户、租户和权限上下文头到下游。
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final WebClient webClient;
    private final List<String> whitelistPaths;

    public JwtAuthFilter(WebClient.Builder webClientBuilder,
                         @Value("${auth.service.url:lb://service-auth}") String authServiceUrl,
                         @Value("${auth.whitelist-paths:/api/v1/auth/**,/health,/actuator/**}") String whitelist) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
        this.whitelistPaths = List.of(whitelist.split(","));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/auth/validate")
                        .queryParam("token", token)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<R<TokenValidateResult>>() {
                })
                .flatMap(response -> {
                    TokenValidateResult result = response != null ? response.getData() : null;
                    if (result == null || !result.isValid()) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r.headers(headers -> {
                                headers.set("X-User-Id", result.getUserId());
                                headers.set("X-Username", result.getUsername());
                                setIfPresent(headers, "X-Tenant-Id", result.getTenantId());
                                headers.set("X-User-Roles", String.join(",", result.getRoles() != null
                                        ? result.getRoles() : List.of()));
                                headers.set("X-User-Permissions", String.join(",", result.getPermissions() != null
                                        ? result.getPermissions() : List.of()));
                                setIfPresent(headers, "X-Auth-Version", result.getAuthVersion());
                                setIfPresent(headers, "X-Role-Version", result.getRoleVersion());
                                setIfPresent(headers, "X-Data-Policy-Version", result.getDataPolicyVersion());
                                setIfPresent(headers, "X-Authorization-Version", result.getAuthorizationVersion());
                                setIfPresent(headers, "X-Field-Policy-Version", result.getFieldPolicyVersion());
                                setIfPresent(headers, "X-Guard-Template-Version", result.getGuardTemplateVersion());
                            }))
                            .build();
                    return chain.filter(mutated);
                })
                .onErrorResume(e -> {
                    log.error("Auth validation failed", e);
                    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    return exchange.getResponse().setComplete();
                });
    }

    private boolean isWhitelisted(String path) {
        return whitelistPaths.stream().anyMatch(pattern -> {
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                return path.startsWith(prefix);
            }
            return path.equals(pattern);
        });
    }

    private void setIfPresent(HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private void setIfPresent(HttpHeaders headers, String name, Long value) {
        if (value != null) {
            headers.set(name, value.toString());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
