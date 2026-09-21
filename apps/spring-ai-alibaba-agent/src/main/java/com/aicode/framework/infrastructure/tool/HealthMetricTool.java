package com.aicode.framework.infrastructure.tool;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 健康指标工具（演示数据）：按患者编号查询血压、血糖等指标。实现 ai-core {@link Tool} 契约。
 */
@Component
public class HealthMetricTool implements Tool {

    private final ObjectMapper objectMapper;

    public HealthMetricTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("HealthMetricTool", "按患者编号查询最新健康指标（血压、血糖、糖化血红蛋白）",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                ToolSupport.PATIENT_ID, Map.of("type", "string", "description", "患者编号，如 P001")),
                        "required", List.of(ToolSupport.PATIENT_ID)));
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String patientId = ToolSupport.requireString(arguments, ToolSupport.PATIENT_ID, "patientId 不能为空");
        Map<String, Object> data = Map.of(
                "patientId", patientId,
                "systolic", 148,
                "diastolic", 92,
                "fastingGlucose", 8.6,
                "hba1c", 7.9);
        return new ToolResult(toJson(data));
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new com.aicode.core.domain.exception.ToolExecutionException("failed to serialize health metric", ex);
        }
    }
}
