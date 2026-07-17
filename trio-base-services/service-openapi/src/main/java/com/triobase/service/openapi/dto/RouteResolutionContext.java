package com.triobase.service.openapi.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record RouteResolutionContext(
        LocalDateTime effectiveAt,
        Map<String, String> headers,
        Map<String, String> query,
        Map<String, String> claims) {

    public RouteResolutionContext {
        effectiveAt = effectiveAt == null ? LocalDateTime.now() : effectiveAt;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        query = query == null ? Map.of() : Map.copyOf(query);
        claims = claims == null ? Map.of() : Map.copyOf(claims);
    }
}
