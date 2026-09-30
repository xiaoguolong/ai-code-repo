package com.aicode.core.infrastructure.security;

import com.aicode.core.domain.exception.GuardrailViolationException;
import com.aicode.core.domain.model.GuardrailContext;
import com.aicode.core.domain.port.GuardrailPort;
import com.aicode.core.infrastructure.config.GuardrailProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 默认 Guardrail 实现：规则驱动的注入检测、长度校验、PII 脱敏与 Tool 参数键白名单。
 */
@Component
public class DefaultGuardrailAdapter implements GuardrailPort {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("忽略(以上|上面|先前|之前)(的)?(所有)?(指令|指示|规则)"),
            Pattern.compile("you\\s+are\\s+now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(reveal|show|print|输出|透露).*system\\s*prompt", Pattern.CASE_INSENSITIVE)
    );

    private final GuardrailProperties properties;
    private final Set<String> allowedToolKeys;

    public DefaultGuardrailAdapter(GuardrailProperties properties) {
        this.properties = properties;
        this.allowedToolKeys = Set.copyOf(properties.resolvedAllowedToolArgumentKeys());
    }

    @Override
    public boolean enabled() {
        return properties.resolvedEnabled();
    }

    @Override
    public void validateTextInput(GuardrailContext context, String fieldName, String text) {
        if (!enabled() || text == null) {
            return;
        }
        if (text.length() > properties.resolvedMaxInputLength()) {
            throw new GuardrailViolationException("input too long in field: " + fieldName);
        }
        if (properties.resolvedBlockPromptInjection() && containsInjection(text)) {
            throw new GuardrailViolationException("prompt injection detected in field: " + fieldName);
        }
    }

    @Override
    public void validateInputMap(GuardrailContext context, Map<String, Object> input) {
        if (!enabled() || input == null) {
            return;
        }
        walkStrings(input, (field, value) -> validateTextInput(context, field, value));
    }

    @Override
    public String sanitizeTextOutput(GuardrailContext context, String text) {
        if (!enabled() || !properties.resolvedSanitizeOutput() || text == null) {
            return text;
        }
        return PiiMasker.mask(text);
    }

    @Override
    public Map<String, Object> sanitizeOutputMap(GuardrailContext context, Map<String, Object> output) {
        if (!enabled() || !properties.resolvedSanitizeOutput() || output == null) {
            return output;
        }
        return sanitizeMapValues(output);
    }

    @Override
    public void validateToolArguments(GuardrailContext context, String toolName, Map<String, Object> arguments) {
        if (!enabled()) {
            return;
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        for (String key : args.keySet()) {
            if (!allowedToolKeys.contains(key)) {
                throw new GuardrailViolationException(
                        "tool argument key not allowed: " + key + " for tool: " + toolName);
            }
        }
        walkStrings(args, (field, value) -> validateTextInput(context, field, value));
    }

    @Override
    public String sanitizeToolOutput(GuardrailContext context, String toolName, String content) {
        return sanitizeTextOutput(context, content);
    }

    private boolean containsInjection(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMapValues(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            result.put(entry.getKey(), sanitizeValue(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(String field, Object value) {
        if (value instanceof String text) {
            return PiiMasker.mask(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                nested.put(String.valueOf(entry.getKey()), sanitizeValue(field + "." + entry.getKey(), entry.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                sanitized.add(sanitizeValue(field + "[" + i + "]", list.get(i)));
            }
            return sanitized;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private void walkStrings(Map<String, Object> map, StringConsumer consumer) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            walkValue(entry.getKey(), entry.getValue(), consumer);
        }
    }

    @SuppressWarnings("unchecked")
    private void walkValue(String field, Object value, StringConsumer consumer) {
        if (value instanceof String text) {
            consumer.accept(field, text);
            return;
        }
        if (value instanceof Map<?, ?> nested) {
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                walkValue(field + "." + entry.getKey(), entry.getValue(), consumer);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                walkValue(field + "[" + i + "]", list.get(i), consumer);
            }
        }
    }

    @FunctionalInterface
    private interface StringConsumer {
        void accept(String field, String value);
    }
}
