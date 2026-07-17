package com.triobase.service.openapi.integration.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataRedactorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SensitiveDataRedactor redactor = new SensitiveDataRedactor();

    @Test
    void redactsSensitiveHeadersKnownFieldsAndConfiguredPointers() throws Exception {
        var payload = objectMapper.readTree("{\"password\":\"pwd\",\"profile\":{\"phone\":\"13800138000\"},\"name\":\"Alice\"}");
        var policy = objectMapper.readTree("{\"sensitivePointers\":[\"/profile/phone\"]}");

        var sanitized = redactor.payload(payload, policy);
        var headers = redactor.headers(Map.of(
                "Authorization", List.of("Bearer secret"),
                "Content-Type", List.of("application/json")));

        assertThat(sanitized.at("/password").asText()).isEqualTo("***REDACTED***");
        assertThat(sanitized.at("/profile/phone").asText()).isEqualTo("***REDACTED***");
        assertThat(sanitized.at("/name").asText()).isEqualTo("Alice");
        assertThat(headers.get("Authorization")).containsExactly("***REDACTED***");
        assertThat(headers.get("Content-Type")).containsExactly("application/json");
    }
}
