package com.triobase.service.apiruntime.action;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Data
@Component
@ConfigurationProperties(prefix = "triobase.openapi.owner-actions")
public class OwnerActionDispatchProperties {

    private Map<String, Endpoint> endpoints = defaultEndpoints();

    public Optional<Endpoint> endpoint(String ownerService) {
        Endpoint endpoint = StringUtils.hasText(ownerService) ? endpoints.get(ownerService) : null;
        return endpoint != null && endpoint.isEnabled() && StringUtils.hasText(endpoint.getBaseUrl())
                ? Optional.of(endpoint) : Optional.empty();
    }

    private static Map<String, Endpoint> defaultEndpoints() {
        Map<String, Endpoint> defaults = new LinkedHashMap<>();
        defaults.put("service-lowcode", new Endpoint(
                "http://localhost:8085/api/v1/lowcode-runtime/actions"));
        defaults.put("service-workflow-engine", new Endpoint(
                "http://localhost:8086/api/v1/workflow-actions"));
        defaults.put("service-api-runtime", new Endpoint(
                "http://localhost:8095/api/v1/openapi/management/actions"));
        return defaults;
    }

    @Data
    public static class Endpoint {
        private boolean enabled = true;
        private String baseUrl;
        private String dispatchPath = "/dispatch";

        public Endpoint() {
        }

        public Endpoint(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String dispatchUrl() {
            String base = trimTrailingSlash(baseUrl);
            String path = StringUtils.hasText(dispatchPath) ? dispatchPath.trim() : "/dispatch";
            return base + (path.startsWith("/") ? path : "/" + path);
        }

        private static String trimTrailingSlash(String value) {
            String normalized = value == null ? "" : value.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
    }
}
