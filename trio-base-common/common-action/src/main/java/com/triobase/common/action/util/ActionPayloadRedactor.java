package com.triobase.common.action.util;

import com.triobase.common.action.definition.ActionSensitivePath;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActionPayloadRedactor {

    public static final String REDACTED = "***REDACTED***";
    public static final int DEFAULT_SUMMARY_LENGTH = 2000;

    private ActionPayloadRedactor() {
    }

    public static Map<String, Object> redact(Map<String, Object> payload,
                                             List<ActionSensitivePath> sensitivePaths) {
        Map<String, Object> copy = deepCopy(payload);
        if (sensitivePaths == null || sensitivePaths.isEmpty()) {
            return copy;
        }
        for (ActionSensitivePath path : sensitivePaths) {
            if (path != null && path.getPath() != null && !path.getPath().isBlank()) {
                redactPath(copy, path.getPath().trim().split("\\."));
            }
        }
        return copy;
    }

    public static String boundedSummary(Object value) {
        return boundedSummary(value, DEFAULT_SUMMARY_LENGTH);
    }

    public static String boundedSummary(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (maxLength < 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) mapValue));
            } else if (value instanceof Collection<?> collectionValue) {
                copy.put(entry.getKey(), collectionValue.stream()
                        .map(item -> item instanceof Map<?, ?> itemMap
                                ? deepCopy((Map<String, Object>) itemMap) : item)
                        .toList());
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void redactPath(Map<String, Object> node, String[] path) {
        if (node == null || path.length == 0) {
            return;
        }
        Object value = node.get(path[0]);
        if (path.length == 1) {
            if (node.containsKey(path[0])) {
                node.put(path[0], REDACTED);
            }
            return;
        }
        if (value instanceof Map<?, ?> mapValue) {
            String[] childPath = new String[path.length - 1];
            System.arraycopy(path, 1, childPath, 0, childPath.length);
            redactPath((Map<String, Object>) mapValue, childPath);
        }
    }
}
