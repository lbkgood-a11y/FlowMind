package com.triobase.service.openapi.integration.credential;

import java.util.Map;

public record OutboundAuthentication(
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String tlsProfileReference) {

    public OutboundAuthentication {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        queryParameters = queryParameters == null ? Map.of() : Map.copyOf(queryParameters);
    }

    public static OutboundAuthentication none() {
        return new OutboundAuthentication(Map.of(), Map.of(), null);
    }
}
