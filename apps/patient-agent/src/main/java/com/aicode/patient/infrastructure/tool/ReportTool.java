package com.aicode.patient.infrastructure.tool;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.patient.domain.port.PatientDataPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 检查报告工具：按患者编号查询检查报告列表。
 */
@Component
public class ReportTool implements Tool {

    private final PatientDataPort patientData;
    private final ObjectMapper objectMapper;

    public ReportTool(PatientDataPort patientData, ObjectMapper objectMapper) {
        this.patientData = patientData;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("ReportTool", "按患者编号查询检查报告列表", PatientToolSupport.patientIdSchema());
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String patientId = PatientToolSupport.requirePatientId(arguments);
        try {
            return new ToolResult(objectMapper.writeValueAsString(patientData.listReports(patientId)));
        } catch (Exception ex) {
            throw new ToolExecutionException("failed to serialize reports", ex);
        }
    }
}
