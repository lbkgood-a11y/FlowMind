package com.triobase.service.openapi.integration.credential;

import java.util.Map;

public record CredentialMaterial(Map<String, String> values) {

    public CredentialMaterial {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public String required(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Credential material is missing required field: " + key);
        }
        return value;
    }
}
