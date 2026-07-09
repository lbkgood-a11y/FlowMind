package com.triobase.platform.gateway.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {
    // Spring Boot auto-configures WebClient.Builder. JwtAuthFilter uses it directly
    // since auth.service.url points to http://localhost:8081 (no load balancing needed).
}
