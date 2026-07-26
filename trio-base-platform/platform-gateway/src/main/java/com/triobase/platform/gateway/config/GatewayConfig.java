package com.triobase.platform.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

@Configuration
public class GatewayConfig {
    // Spring Boot auto-configures WebClient.Builder. JwtAuthFilter uses it directly
    // since auth.service.url points to http://localhost:8081 (no load balancing needed).

    @Bean
    @ConditionalOnProperty(prefix = "triobase.openapi.runtime", name = "enabled", havingValue = "true")
    RouteLocator openApiRuntimeRoutes(
            RouteLocatorBuilder builder,
            @Value("${triobase.openapi.runtime.service-url:http://localhost:8095}") String runtimeServiceUrl) {
        return builder.routes()
                .route("service-api-runtime", route -> route
                        .path("/api/v1/openapi/runtime/**", "/api/v1/openapi/callbacks/**")
                        .uri(runtimeServiceUrl))
                .build();
    }
}
