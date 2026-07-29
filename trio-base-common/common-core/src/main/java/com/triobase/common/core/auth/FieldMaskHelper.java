package com.triobase.common.core.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FieldMaskHelper {

    private static final Logger log = LoggerFactory.getLogger(FieldMaskHelper.class);
    private static final String MASK_PLACEHOLDER = "******";
    private static final Set<String> READ_DENIED_MODES = Set.of("HIDDEN", "DENIED");
    private static final Set<String> WRITE_DENIED_MODES = Set.of("READ_ONLY", "DENIED");

    private FieldMaskHelper() {
    }

    public static Map<String, Object> applyReadRules(Map<String, Object> data,
                                                      List<? extends FieldRule> fieldRules) {
        if (data == null || data.isEmpty() || fieldRules == null || fieldRules.isEmpty()) {
            return data != null ? data : Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(data);
        for (FieldRule rule : fieldRules) {
            if (rule == null || rule.getFieldKey() == null || rule.getFieldKey().isBlank()) {
                continue;
            }
            String fieldKey = rule.getFieldKey();
            String readMode = normalize(rule.getReadMode());
            if (READ_DENIED_MODES.contains(readMode)) {
                result.remove(fieldKey);
            } else if ("MASKED".equals(readMode) && result.containsKey(fieldKey)) {
                result.put(fieldKey, mask(result.get(fieldKey), rule.getMaskStrategy()));
            }
        }
        return result;
    }

    public static void assertWritableFields(Map<String, Object> data,
                                             List<? extends FieldRule> fieldRules) {
        if (data == null || data.isEmpty()) {
            return;
        }
        if (fieldRules == null || fieldRules.isEmpty()) {
            throw new IllegalArgumentException("Field rules are required for write operations");
        }
        Map<String, FieldRule> rulesByKey = indexByKey(fieldRules);
        for (String fieldKey : data.keySet()) {
            FieldRule rule = rulesByKey.get(fieldKey);
            if (rule == null) {
                throw new IllegalArgumentException("Field '" + fieldKey
                        + "' is not registered in field rules — write denied by default");
            }
            if (WRITE_DENIED_MODES.contains(normalize(rule.getWriteMode()))) {
                throw new IllegalArgumentException("Field write denied: " + fieldKey);
            }
        }
    }

    public static boolean isReadable(String readMode) {
        return !READ_DENIED_MODES.contains(normalize(readMode));
    }

    public static Object mask(Object value, String strategy) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        String normalized = normalize(strategy);
        return switch (normalized) {
            case "LAST4" -> last4(text);
            case "PHONE" -> phone(text);
            case "EMAIL" -> email(text);
            default -> {
                log.warn("Unknown field mask strategy '{}' — falling back to placeholder", strategy);
                yield MASK_PLACEHOLDER;
            }
        };
    }

    private static String last4(String text) {
        if (text.length() <= 4) {
            return MASK_PLACEHOLDER;
        }
        return "*".repeat(text.length() - 4) + text.substring(text.length() - 4);
    }

    private static String phone(String text) {
        if (text.length() < 7) {
            return MASK_PLACEHOLDER;
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }

    private static String email(String text) {
        int at = text.indexOf('@');
        if (at <= 0) {
            return MASK_PLACEHOLDER;
        }
        return text.charAt(0) + "***" + text.substring(at);
    }

    private static Map<String, FieldRule> indexByKey(List<? extends FieldRule> fieldRules) {
        Map<String, FieldRule> rules = new LinkedHashMap<>();
        for (FieldRule rule : fieldRules) {
            if (rule != null && rule.getFieldKey() != null && !rule.getFieldKey().isBlank()) {
                rules.put(rule.getFieldKey(), rule);
            }
        }
        return rules;
    }

    private static String normalize(String value) {
        return value != null && !value.isBlank() ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
