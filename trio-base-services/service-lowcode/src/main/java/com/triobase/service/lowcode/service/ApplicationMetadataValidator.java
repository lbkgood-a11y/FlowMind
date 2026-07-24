package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.dto.ApplicationPageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationMetadataValidator {

    private static final Set<String> SUPPORTED_PAGE_TYPES = Set.of("LIST", "DETAIL", "CREATE");
    private static final Set<String> LIST_FORMATS = Set.of("", "TEXT", "DATE", "DATETIME", "MONEY", "STATUS", "BOOLEAN");
    private static final Set<String> FILTER_OPERATORS = Set.of("EQ", "CONTAINS", "GTE", "LTE");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "script", "scripts", "javascript", "eval", "function", "handler", "onClick",
            "sql", "querySql", "whereSql", "url", "endpoint", "webhook", "callbackUrl",
            "className", "executorClass", "beanName", "connector", "connectorId",
            "credential", "credentials", "secret", "apiKey", "token", "prompt", "freePrompt");

    private final ObjectMapper objectMapper;

    public void validateDraft(List<ApplicationPageRequest> pages, List<ApplicationActionRequest> actions) {
        if (pages == null || pages.isEmpty()) {
            throw new BizException(40050, "APPLICATION_PAGE_REQUIRED");
        }
        Set<String> pageTypes = new HashSet<>();
        for (ApplicationPageRequest page : pages) {
            String pageType = normalized(page != null ? page.getPageType() : null);
            if (!SUPPORTED_PAGE_TYPES.contains(pageType)) {
                throw new BizException(40050, "APPLICATION_PAGE_TYPE_UNSUPPORTED");
            }
            if (!pageTypes.add(pageType)) {
                throw new BizException(40050, "APPLICATION_PAGE_TYPE_DUPLICATE");
            }
            JsonNode metadata = readObject(page.getMetadataJson(), "APPLICATION_PAGE_METADATA_INVALID");
            rejectForbiddenMetadata(metadata);
            validatePageShape(pageType, metadata);
        }
        if (actions != null) {
            Set<String> actionCodes = new HashSet<>();
            for (ApplicationActionRequest action : actions) {
                validateAction(action, actionCodes);
            }
        }
    }

    public void validateFieldReferences(String schemaJson, List<ApplicationPageRequest> pages) {
        JsonNode schema = readObject(schemaJson, "APPLICATION_FORM_SCHEMA_INVALID");
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            throw new BizException(40050, "APPLICATION_FORM_SCHEMA_INVALID");
        }
        Set<String> allowedFields = new HashSet<>();
        properties.fieldNames().forEachRemaining(allowedFields::add);
        for (ApplicationPageRequest page : pages) {
            JsonNode metadata = readObject(page.getMetadataJson(), "APPLICATION_PAGE_METADATA_INVALID");
            validateFieldReferences(metadata, allowedFields);
        }
    }

    private void validatePageShape(String pageType, JsonNode metadata) {
        switch (pageType) {
            case "LIST" -> {
                JsonNode columns = metadata.path("columns");
                if (!columns.isArray() || columns.isEmpty()) {
                    throw new BizException(40050, "APPLICATION_LIST_COLUMNS_REQUIRED");
                }
                validateFieldArray(columns, "APPLICATION_LIST_COLUMN_INVALID");
                for (JsonNode column : columns) {
                    String format = normalized(column.path("format").asText());
                    if (!LIST_FORMATS.contains(format)) {
                        throw new BizException(40050, "APPLICATION_LIST_FORMAT_INVALID");
                    }
                    int width = column.path("width").asInt(140);
                    if (width < 60 || width > 800) {
                        throw new BizException(40050, "APPLICATION_LIST_WIDTH_INVALID");
                    }
                }
                JsonNode filters = metadata.path("filters");
                if (!filters.isMissingNode() && !filters.isArray()) {
                    throw new BizException(40050, "APPLICATION_LIST_FILTER_INVALID");
                }
                if (filters.isArray()) {
                    validateFieldArray(filters, "APPLICATION_LIST_FILTER_INVALID");
                    for (JsonNode filter : filters) {
                        if (!FILTER_OPERATORS.contains(normalized(filter.path("operator").asText("EQ")))) {
                            throw new BizException(40050, "APPLICATION_LIST_FILTER_OPERATOR_INVALID");
                        }
                    }
                }
                int pageSize = metadata.path("pageSize").asInt(20);
                if (pageSize < 1 || pageSize > 200) {
                    throw new BizException(40050, "APPLICATION_LIST_PAGE_SIZE_INVALID");
                }
                JsonNode defaultSort = metadata.path("defaultSort");
                if (!defaultSort.isMissingNode() && !defaultSort.isNull()) {
                    if (!defaultSort.isObject() || !StringUtils.hasText(defaultSort.path("fieldKey").asText())
                            || !Set.of("ASC", "DESC").contains(normalized(defaultSort.path("direction").asText()))) {
                        throw new BizException(40050, "APPLICATION_LIST_SORT_INVALID");
                    }
                }
            }
            case "DETAIL", "CREATE" -> {
                JsonNode sections = metadata.path("sections");
                if (!sections.isArray() || sections.isEmpty()) {
                    throw new BizException(40050, "APPLICATION_PAGE_SECTIONS_REQUIRED");
                }
                for (JsonNode section : sections) {
                    if (!section.isObject()) {
                        throw new BizException(40050, "APPLICATION_PAGE_SECTION_INVALID");
                    }
                    JsonNode fields = section.path("fields");
                    if (!fields.isArray() || fields.isEmpty()) {
                        throw new BizException(40050, "APPLICATION_PAGE_FIELDS_REQUIRED");
                    }
                    validateFieldArray(fields, "APPLICATION_PAGE_FIELD_INVALID");
                }
            }
            default -> throw new BizException(40050, "APPLICATION_PAGE_TYPE_UNSUPPORTED");
        }
    }

    private void validateFieldArray(JsonNode nodes, String errorCode) {
        for (JsonNode node : nodes) {
            if (!node.isObject() || !StringUtils.hasText(node.path("fieldKey").asText())) {
                throw new BizException(40050, errorCode);
            }
        }
    }

    private void validateAction(ApplicationActionRequest action, Set<String> actionCodes) {
        if (action == null || !StringUtils.hasText(action.getActionCode())
                || !StringUtils.hasText(action.getActionType())
                || !StringUtils.hasText(action.getLabel())) {
            throw new BizException(40050, "APPLICATION_ACTION_REQUIRED");
        }
        if (!actionCodes.add(action.getActionCode().trim())) {
            throw new BizException(40050, "APPLICATION_ACTION_DUPLICATE");
        }
        String actionType = normalized(action.getActionType());
        if (!LowcodeAuthorizationActionCatalog.supportedApplicationActionTypes().contains(actionType)) {
            throw new BizException(40050, "APPLICATION_ACTION_TYPE_UNSUPPORTED");
        }
        LowcodeAuthorizationActionCatalog.formActionForApplicationActionType(actionType);
        if (requiresProcessKey(actionType) && !StringUtils.hasText(action.getProcessKey())) {
            throw new BizException(40050, "APPLICATION_ACTION_PROCESS_KEY_REQUIRED");
        }
        if (!requiresProcessKey(actionType) && StringUtils.hasText(action.getProcessKey())) {
            throw new BizException(40050, "APPLICATION_ACTION_PROCESS_KEY_UNSUPPORTED");
        }
        if (StringUtils.hasText(action.getMetadataJson())) {
            JsonNode metadata = readObject(action.getMetadataJson(), "APPLICATION_ACTION_METADATA_INVALID");
            rejectForbiddenMetadata(metadata);
        }
    }

    private boolean requiresProcessKey(String actionType) {
        return "SUBMIT_AND_LAUNCH_WORKFLOW".equals(actionType)
                || "RETRY_WORKFLOW".equals(actionType);
    }

    private void validateFieldReferences(JsonNode node, Set<String> allowedFields) {
        if (node.isObject()) {
            JsonNode fieldKey = node.get("fieldKey");
            if (fieldKey != null && (!fieldKey.isTextual() || !allowedFields.contains(fieldKey.asText()))) {
                throw new BizException(40050, "APPLICATION_FIELD_REFERENCE_INVALID");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                validateFieldReferences(fields.next().getValue(), allowedFields);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                validateFieldReferences(item, allowedFields);
            }
        }
    }

    private void rejectForbiddenMetadata(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isForbiddenKey(field.getKey())) {
                    throw new BizException(40050, "APPLICATION_METADATA_FORBIDDEN_FIELD");
                }
                rejectForbiddenMetadata(field.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                rejectForbiddenMetadata(item);
            }
        } else if (node.isTextual() && looksExecutable(node.asText())) {
            throw new BizException(40050, "APPLICATION_METADATA_FORBIDDEN_VALUE");
        }
    }

    private boolean isForbiddenKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalized = key.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        return FORBIDDEN_KEYS.stream()
                .anyMatch(forbidden -> lower.equals(forbidden.toLowerCase(Locale.ROOT))
                        || lower.contains(forbidden.toLowerCase(Locale.ROOT)));
    }

    private boolean looksExecutable(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("<script")
                || lower.contains("javascript:")
                || lower.contains("select ")
                || lower.contains("insert ")
                || lower.contains("update ")
                || lower.contains("delete ")
                || lower.contains("http://")
                || lower.contains("https://");
    }

    private JsonNode readObject(String json, String errorCode) {
        if (!StringUtils.hasText(json)) {
            throw new BizException(40050, errorCode);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new BizException(40050, errorCode);
            }
            return node;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(40050, errorCode);
        }
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
