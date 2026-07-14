package com.triobase.service.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.ConditionEvaluationResult;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.JexlFeatures;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class RestrictedConditionEvaluator {

    static final int MAX_EXPRESSION_LENGTH = 512;

    private static final Pattern FORBIDDEN_IDENTIFIER = Pattern.compile(
            "(?i)\\b(new|class|forName|getClass|runtime|system|processBuilder|reflect|"
                    + "classLoader|file|socket|url|https?|import|pragma)\\b");
    private static final Pattern METHOD_INVOCATION = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?<![=!<>])=(?!=)");
    private static final Pattern FORBIDDEN_SYNTAX = Pattern.compile("[`@;{}#]|::|->");

    private final ObjectMapper objectMapper;
    private final JexlEngine jexl;

    public RestrictedConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        JexlFeatures features = JexlFeatures.createDefault()
                .annotation(false)
                .importPragma(false)
                .lambda(false)
                .loops(false)
                .methodCall(false)
                .namespaceIdentifier(false)
                .namespacePragma(false)
                .newInstance(false)
                .pragma(false)
                .script(false)
                .sideEffect(false)
                .sideEffectGlobal(false)
                .structuredLiteral(false);
        this.jexl = new JexlBuilder()
                .cache(128)
                .features(features)
                .permissions(JexlPermissions.RESTRICTED)
                .safe(false)
                .silent(false)
                .strict(true)
                .create();
    }

    public ConditionEvaluationResult evaluate(String expression, String formDataJson) {
        boolean defaultBranch = "true".equals(expression != null ? expression.trim() : null);
        try {
            JexlExpression compiled = compile(expression);
            Map<String, Object> formData = parseFormData(formDataJson);
            Object value = compiled.evaluate(new MapContext(Collections.unmodifiableMap(formData)));
            if (!(value instanceof Boolean matched)) {
                return ConditionEvaluationResult.error(expression, "CONDITION_RESULT_NOT_BOOLEAN");
            }
            return matched
                    ? ConditionEvaluationResult.matched(expression, defaultBranch)
                    : ConditionEvaluationResult.notMatched(expression);
        } catch (BizException | JexlException e) {
            return ConditionEvaluationResult.error(expression, errorCode(e));
        }
    }

    public void validate(String expression) {
        compile(expression);
    }

    private JexlExpression compile(String expression) {
        if (!StringUtils.hasText(expression)) {
            throw new BizException(40000, "CONDITION_EXPRESSION_REQUIRED");
        }
        String normalized = expression.trim();
        if (normalized.length() > MAX_EXPRESSION_LENGTH) {
            throw new BizException(40000, "CONDITION_EXPRESSION_TOO_LONG");
        }
        if (FORBIDDEN_IDENTIFIER.matcher(normalized).find()
                || METHOD_INVOCATION.matcher(normalized).find()
                || ASSIGNMENT.matcher(normalized).find()
                || FORBIDDEN_SYNTAX.matcher(normalized).find()) {
            throw new BizException(40000, "UNSAFE_CONDITION_EXPRESSION");
        }
        try {
            return jexl.createExpression(normalized);
        } catch (JexlException e) {
            throw new BizException(40000, "INVALID_CONDITION_EXPRESSION");
        }
    }

    private Map<String, Object> parseFormData(String formDataJson) {
        if (!StringUtils.hasText(formDataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    formDataJson, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new BizException(40000, "INVALID_FORM_DATA_JSON");
        }
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BizException bizException) {
            return bizException.getMessage();
        }
        return "CONDITION_EVALUATION_FAILED";
    }
}
