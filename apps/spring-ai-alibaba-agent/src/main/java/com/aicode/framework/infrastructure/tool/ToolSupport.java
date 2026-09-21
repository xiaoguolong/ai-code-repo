package com.aicode.framework.infrastructure.tool;

import com.aicode.core.domain.exception.ToolExecutionException;

import java.util.List;
import java.util.Map;

/**
 * 工具参数与 JSON Schema 的公共组装，避免各工具重复样板。
 */
final class ToolSupport {

    static final String PATIENT_ID = "patientId";

    private ToolSupport() {
    }

    /**
     * 取必填字符串参数，缺失或空白抛 {@link ToolExecutionException}。
     */
    static String requireString(Map<String, Object> arguments, String key, String message) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ToolExecutionException(message);
        }
        return value.toString().trim();
    }

    /**
     * 单参数（patientId）JSON Schema。
     */
    static Map<String, Object> patientIdSchema(String description) {
        return Map.of(
                "type", "object",
                "properties", Map.of(PATIENT_ID, Map.of("type", "string", "description", description)),
                "required", List.of(PATIENT_ID));
    }
}
