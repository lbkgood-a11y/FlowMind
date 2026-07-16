package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.enums.MappingOperation;
import com.triobase.service.openapi.dto.MappingRuleRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class MappingSecurityValidator {

    private static final int UNSAFE_MAPPING = 40021;
    private static final Set<String> FORBIDDEN_CONFIG_KEYS = Set.of(
            "script", "javascript", "spel", "ognl", "eval", "classname", "classloader",
            "authorization", "credential", "credentials", "password", "secret", "apikey", "api-key");
    private static final Set<String> CREDENTIAL_TARGET_MARKERS = Set.of(
            "authorization", "credential", "password", "secret", "token", "apikey", "api-key");

    public void validate(MappingRuleRequest rule) {
        if (rule == null || rule.operation() == null) {
            fail("OPENAPI_MAPPING_OPERATION_NOT_REGISTERED");
        }
        scanConfig(rule.config());
        if ((rule.operation() == MappingOperation.CONSTANT || rule.operation() == MappingOperation.DEFAULT)
                && containsMarker(rule.targetPointer(), CREDENTIAL_TARGET_MARKERS)) {
            fail("OPENAPI_MAPPING_CREDENTIAL_CONSTANT_FORBIDDEN:" + rule.targetPointer());
        }
    }

    private void scanConfig(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String normalized = entry.getKey().toLowerCase(Locale.ROOT).replace("_", "");
                if (FORBIDDEN_CONFIG_KEYS.contains(normalized)) {
                    fail("OPENAPI_MAPPING_UNSAFE_CONFIG_KEY:" + entry.getKey());
                }
                scanConfig(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::scanConfig);
        } else if (node.isTextual()) {
            String text = node.asText().toLowerCase(Locale.ROOT);
            if (text.contains("javascript:") || text.contains("${") || text.contains("#{")
                    || text.contains("java.lang.") || text.contains("class.forname")) {
                fail("OPENAPI_MAPPING_UNSAFE_EXPRESSION");
            }
        }
    }

    private boolean containsMarker(String value, Set<String> markers) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace("_", "").replace("/", "");
        return markers.stream().anyMatch(normalized::contains);
    }

    private void fail(String message) {
        throw new BizException(UNSAFE_MAPPING, message);
    }
}
