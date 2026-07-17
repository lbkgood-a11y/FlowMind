package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.dto.RouteResolutionContext;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class RoutePredicateEvaluator {

    private static final Set<String> SOURCES = Set.of("HEADER", "QUERY", "CLAIM");
    private static final Set<String> OPERATORS = Set.of("EQUALS", "IN", "PRESENT");

    public void validate(JsonNode predicate) {
        if (predicate == null || predicate.isNull() || predicate.isEmpty()) {
            return;
        }
        JsonNode rules = predicate.get("all");
        if (rules == null || !rules.isArray() || rules.size() > 20 || predicate.size() != 1) {
            invalid();
        }
        for (JsonNode rule : rules) {
            String source = upper(rule, "source");
            String operator = upper(rule, "operator");
            String name = rule.path("name").asText();
            if (!rule.isObject() || !SOURCES.contains(source) || !OPERATORS.contains(operator)
                    || name.isBlank() || name.length() > 128
                    || (!"PRESENT".equals(operator) && !hasValue(rule, operator))) {
                invalid();
            }
            Iterator<String> fields = rule.fieldNames();
            Set<String> allowed = Set.of("source", "name", "operator", "value", "values");
            while (fields.hasNext()) {
                if (!allowed.contains(fields.next())) {
                    invalid();
                }
            }
        }
    }

    public boolean matches(JsonNode predicate, RouteResolutionContext context) {
        validate(predicate);
        if (predicate == null || predicate.isNull() || predicate.isEmpty()) {
            return true;
        }
        for (JsonNode rule : predicate.path("all")) {
            String name = rule.path("name").asText();
            String actual = source(context, upper(rule, "source")).get(
                    "HEADER".equals(upper(rule, "source")) ? name.toLowerCase(Locale.ROOT) : name);
            String operator = upper(rule, "operator");
            boolean matches = switch (operator) {
                case "PRESENT" -> actual != null && !actual.isBlank();
                case "EQUALS" -> actual != null && actual.equals(rule.path("value").asText());
                case "IN" -> actual != null && values(rule.path("values")).contains(actual);
                default -> false;
            };
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    public boolean canOverlap(JsonNode left, JsonNode right) {
        validate(left);
        validate(right);
        Map<String, Constraint> leftConstraints = constraints(left);
        Map<String, Constraint> rightConstraints = constraints(right);
        for (Map.Entry<String, Constraint> entry : leftConstraints.entrySet()) {
            Constraint other = rightConstraints.get(entry.getKey());
            if (other != null && entry.getValue().disjoint(other)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Constraint> constraints(JsonNode predicate) {
        Map<String, Constraint> result = new java.util.HashMap<>();
        if (predicate == null || predicate.isNull() || predicate.isEmpty()) {
            return result;
        }
        predicate.path("all").forEach(rule -> {
            String operator = upper(rule, "operator");
            if ("EQUALS".equals(operator)) {
                result.put(key(rule), new Constraint(Set.of(rule.path("value").asText())));
            } else if ("IN".equals(operator)) {
                result.put(key(rule), new Constraint(values(rule.path("values"))));
            }
        });
        return result;
    }

    private String key(JsonNode rule) {
        return upper(rule, "source") + ":" + rule.path("name").asText().toLowerCase(Locale.ROOT);
    }

    private Map<String, String> source(RouteResolutionContext context, String source) {
        return switch (source) {
            case "HEADER" -> caseInsensitive(context.headers());
            case "QUERY" -> context.query();
            case "CLAIM" -> context.claims();
            default -> Map.of();
        };
    }

    private Map<String, String> caseInsensitive(Map<String, String> input) {
        Map<String, String> result = new java.util.HashMap<>();
        input.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT), value));
        return result;
    }

    private boolean hasValue(JsonNode rule, String operator) {
        if ("EQUALS".equals(operator)) {
            return rule.has("value") && rule.get("value").isTextual();
        }
        return rule.has("values") && rule.get("values").isArray()
                && !rule.get("values").isEmpty() && rule.get("values").size() <= 50;
    }

    private Set<String> values(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private String upper(JsonNode node, String field) {
        return node.path(field).asText().toUpperCase(Locale.ROOT);
    }

    private void invalid() {
        throw new BizException(40040, "OPENAPI_ROUTE_PREDICATE_INVALID");
    }

    private record Constraint(Set<String> values) {
        private boolean disjoint(Constraint other) {
            return values.stream().noneMatch(other.values::contains);
        }
    }
}
