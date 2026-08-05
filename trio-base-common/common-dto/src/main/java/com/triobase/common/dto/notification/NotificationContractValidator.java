package com.triobase.common.dto.notification;

import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在持久化或投递前执行通知公共契约的默认拒绝校验。
 *
 * <p>此校验器不替代 Owner 授权；它确保跨语言载荷没有越出租户、注册解析器、声明变量和
 * 标准 JSON 数据边界。调用方必须在错误列表非空时拒绝请求。</p>
 */
public final class NotificationContractValidator {

    private static final Set<String> EXECUTION_MARKERS = Set.of(
            "http://", "https://", "javascript:", "select ", "insert ", "update ", "delete ",
            "drop ", "exec ", "eval(", "<script");
    private static final Set<String> SENSITIVE_KEY_MARKERS = Set.of(
            "password", "passwd", "secret", "token", "credential", "apikey", "api_key",
            "phone", "mobile", "idcard", "id_card", "bankcard", "bank_card");

    private NotificationContractValidator() {
    }

    public static List<String> validate(NotificationRequest request, Set<String> registeredResolverKeys) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            return List.of("REQUEST_REQUIRED");
        }
        required(request.getTenantId(), "TENANT_REQUIRED", errors);
        required(request.getProducer(), "PRODUCER_REQUIRED", errors);
        required(request.getEventId(), "EVENT_ID_REQUIRED", errors);
        required(request.getIdempotencyKey(), "IDEMPOTENCY_KEY_REQUIRED", errors);
        required(request.getTemplateKey(), "TEMPLATE_KEY_REQUIRED", errors);
        if (!NotificationRequest.CURRENT_SCHEMA_VERSION.equals(request.getSchemaVersion())) {
            errors.add("UNSUPPORTED_SCHEMA_VERSION");
        }
        validateAudience(request, registeredResolverKeys, errors);
        if (!request.getDeclaredVariables().containsAll(request.getVariables().keySet())) {
            errors.add("UNDECLARED_TEMPLATE_VARIABLE");
        }
        request.getVariables().forEach((key, value) -> {
            if (containsSensitiveKey(key) || containsSensitiveMapKey(value)) {
                errors.add("SENSITIVE_VARIABLE_FORBIDDEN:" + key);
            } else if (!isJsonCompatible(value)) {
                errors.add("UNSUPPORTED_VARIABLE_TYPE:" + key);
            } else if (containsExecutionMarker(value)) {
                errors.add("UNSAFE_VARIABLE_CONTENT:" + key);
            }
        });
        BusinessResourceReference reference = request.getResourceReference();
        if (reference != null && (containsExecutionMarker(reference.resourceKey())
                || containsExecutionMarker(reference.actionId()))) {
            errors.add("UNREGISTERED_EXECUTION_REFERENCE");
        }
        return List.copyOf(errors);
    }

    private static boolean containsSensitiveMapKey(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(entry -> containsSensitiveKey(entry.getKey().toString())
                    || containsSensitiveMapKey(entry.getValue()));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(NotificationContractValidator::containsSensitiveMapKey);
        }
        return false;
    }

    private static boolean containsSensitiveKey(String key) {
        String normalized = key == null ? "" : key.replace("-", "_").toLowerCase();
        return SENSITIVE_KEY_MARKERS.stream().anyMatch(normalized::contains);
    }

    private static void validateAudience(NotificationRequest request,
                                         Set<String> registeredResolverKeys,
                                         List<String> errors) {
        AudienceSelector audience = request.getAudience();
        if (audience == null || audience.getType() == null) {
            errors.add("AUDIENCE_REQUIRED");
            return;
        }
        if (!request.getTenantId().equals(audience.getScopeTenantId())) {
            errors.add("CROSS_TENANT_AUDIENCE");
        }
        if (audience.getType() == AudienceSelector.AudienceType.DYNAMIC_PARTICIPANT
                && (audience.getResolverKey() == null
                || !registeredResolverKeys.contains(audience.getResolverKey()))) {
            errors.add("UNREGISTERED_AUDIENCE_RESOLVER");
        }
    }

    private static boolean isJsonCompatible(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof TemporalAccessor) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.keySet().stream().allMatch(String.class::isInstance)
                    && map.values().stream().allMatch(NotificationContractValidator::isJsonCompatible);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().allMatch(NotificationContractValidator::isJsonCompatible);
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                if (!isJsonCompatible(Array.get(value, i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean containsExecutionMarker(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(NotificationContractValidator::containsExecutionMarker);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(NotificationContractValidator::containsExecutionMarker);
        }
        String normalized = value.toString().toLowerCase();
        return EXECUTION_MARKERS.stream().anyMatch(normalized::contains);
    }

    private static void required(String value, String error, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(error);
        }
    }
}
