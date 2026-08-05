package com.triobase.service.ops.notification.service;

import com.triobase.common.core.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 非可执行模板渲染器，仅识别简单变量占位符并进行 HTML 字符转义。
 * 静态模板中的脚本、事件属性和表达式语法全部默认拒绝，避免预览与投递路径语义不一致。
 */
@Component
public class SafeNotificationTemplateRenderer {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_.]{0,63})\\}\\}");
    private static final Pattern UNSAFE = Pattern.compile(
            "(?is)<\\s*(script|iframe|object|embed)|javascript\\s*:|on[a-z]+\\s*=|\\$\\{|#\\{|<%|\\{%");

    public Set<String> validate(String subject, String body, Map<String, String> schema) {
        String combined = (subject == null ? "" : subject) + "\n" + body;
        if (UNSAFE.matcher(combined).find() || combined.contains("{{{")) {
            throw new BizException(45510, "TEMPLATE_UNSAFE_CONTENT");
        }
        Set<String> variables = variables(combined);
        Set<String> declared = schema == null ? Set.of() : schema.keySet();
        if (!declared.containsAll(variables)) {
            throw new BizException(45511, "TEMPLATE_VARIABLE_UNDECLARED");
        }
        if (schema != null && schema.entrySet().stream().anyMatch(entry ->
                !entry.getKey().matches("[A-Za-z][A-Za-z0-9_.]{0,63}")
                        || !Set.of("STRING", "NUMBER", "BOOLEAN").contains(entry.getValue()))) {
            throw new BizException(45512, "TEMPLATE_VARIABLE_SCHEMA_INVALID");
        }
        return variables;
    }

    public String render(String template, Map<String, String> schema, Map<String, Object> values) {
        validate(null, template, schema);
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = values == null ? null : values.get(name);
            if (value == null || !matchesType(schema.get(name), value)) {
                throw new BizException(45513, "TEMPLATE_VARIABLE_VALUE_INVALID");
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(escape(String.valueOf(value))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Set<String> variables(String template) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(template);
        while (matcher.find()) variables.add(matcher.group(1));
        return variables;
    }

    private boolean matchesType(String type, Object value) {
        return switch (type) {
            case "STRING" -> value instanceof String;
            case "NUMBER" -> value instanceof Number;
            case "BOOLEAN" -> value instanceof Boolean;
            default -> false;
        };
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
