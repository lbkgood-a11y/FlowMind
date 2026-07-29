package com.triobase.common.core.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
@ConfigurationProperties(prefix = "triobase.internal")
public class InternalServiceSecurityProperties {
    private boolean enabled = true;
    private String token;
    private List<String> allowedCallers = new ArrayList<>();
    private DataScope dataScope = new DataScope();

    @PostConstruct
    void validate() {
        if (enabled && (token == null || token.isBlank())) {
            log.error("triobase.internal.token must be configured when triobase.internal.enabled=true. "
                    + "A hardcoded default is not permitted for security reasons.");
            throw new IllegalStateException(
                    "triobase.internal.token is required when internal service security is enabled");
        }
    }

    @Data
    public static class DataScope {
        private String authBaseUrl = "http://localhost:8081";
        private String serviceName = "common-core";
    }
}
