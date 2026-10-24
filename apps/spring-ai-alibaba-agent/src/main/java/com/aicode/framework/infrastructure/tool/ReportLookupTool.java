package com.aicode.framework.infrastructure.tool;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 检查报告工具（Week 21，演示数据）：按患者编号查询检查/检验报告列表。
 *
 * <p>Agent 1 要求「查询检查报告」，而 Week 6 的工具集只有患者与健康指标，
 * 故本周补一个确定性数据源。真实环境把 {@code execute} 换成 HIS/LIS 查询即可，
 * 入参/出参契约不变。</p>
 */
@Component
public class ReportLookupTool implements Tool {

    private final ObjectMapper objectMapper;

    public ReportLookupTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("ReportLookupTool", "按患者编号查询检查/检验报告列表（含异常标记）",
                ToolSupport.patientIdSchema("患者编号，如 P001"));
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String patientId = ToolSupport.requireString(
                arguments, ToolSupport.PATIENT_ID, "patientId 不能为空");
        Map<String, Object> data = Map.of(
                "patientId", patientId,
                "reports", List.of(
                        Map.of(
                                "reportId", "R001",
                                "type", "糖化血红蛋白",
                                "date", "2026-09-12",
                                "conclusion", "HbA1c 8.1%，血糖控制不佳",
                                "abnormal", true),
                        Map.of(
                                "reportId", "R002",
                                "type", "肾功能",
                                "date", "2026-09-20",
                                "conclusion", "eGFR 78 mL/min/1.73m²，轻度下降",
                                "abnormal", false)));
        return new ToolResult(toJson(data));
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new com.aicode.core.domain.exception.ToolExecutionException(
                    "failed to serialize report", ex);
        }
    }
}
