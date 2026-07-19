package com.triobase.service.action.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "triobase.action.owner")
public class ActionOwnerServiceProperties {
    private Map<String, String> baseUrls = new LinkedHashMap<>();

    public String requireBaseUrl(String ownerService) {
        String baseUrl = baseUrls.get(ownerService);
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }
}
