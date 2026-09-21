package com.aicode.framework.infrastructure.tool;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 患者信息工具（演示数据）：按患者编号查询患者基础信息。实现 ai-core {@link Tool} 契约，
 * 可被 Graph Agent 的 {@code ToolPort} 调度，也可经 MCP 暴露。
 */
@Component
public class PatientLookupTool implements Tool {

    private final ObjectMapper objectMapper;

    public PatientLookupTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("PatientLookupTool", "按患者编号查询患者基础信息",
                ToolSupport.patientIdSchema("患者编号，如 P001"));
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String patientId = ToolSupport.requireString(arguments, ToolSupport.PATIENT_ID, "patientId 不能为空");
        Map<String, Object> data = Map.of(
                "patientId", patientId,
                "name", "张三",
                "age", 62,
                "gender", "male",
                "diagnosis", "2 型糖尿病");
        return new ToolResult(toJson(data));
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new com.aicode.core.domain.exception.ToolExecutionException("failed to serialize patient", ex);
        }
    }
}
