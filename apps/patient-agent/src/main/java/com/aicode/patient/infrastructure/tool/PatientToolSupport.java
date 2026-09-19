package com.aicode.patient.infrastructure.tool;

import java.util.List;
import java.util.Map;

/**
 * 患者工具 JSON Schema 与参数处理工具。四个工具统一使用 patientId 作为唯一入参。
 */
final class PatientToolSupport {

    private PatientToolSupport() {
    }

    /**
     * patientId 单参数的 JSON Schema。
     */
    static Map<String, Object> patientIdSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "patientId", Map.of(
                                "type", "string",
                                "description", "患者编号，例如 P001"
                        )
                ),
                "required", List.of("patientId")
        );
    }

    /**
     * 从参数中取 patientId，缺失时抛异常。
     */
    static String requirePatientId(Map<String, Object> arguments) {
        Object value = arguments.get("patientId");
        if (value == null || value.toString().isBlank()) {
            throw new com.aicode.core.domain.exception.ToolExecutionException("missing patientId");
        }
        return value.toString().trim();
    }
}
