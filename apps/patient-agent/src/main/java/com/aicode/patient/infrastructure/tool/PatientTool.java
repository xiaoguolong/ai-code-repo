package com.aicode.patient.infrastructure.tool;

import com.aicode.core.domain.Tool;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.patient.domain.model.Patient;
import com.aicode.patient.domain.port.PatientDataPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 患者信息工具：按患者编号查询患者基础信息。
 */
@Component
public class PatientTool implements Tool {

    private final PatientDataPort patientData;
    private final ObjectMapper objectMapper;

    public PatientTool(PatientDataPort patientData, ObjectMapper objectMapper) {
        this.patientData = patientData;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("PatientTool", "按患者编号查询患者基础信息", PatientToolSupport.patientIdSchema());
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String patientId = PatientToolSupport.requirePatientId(arguments);
        Optional<Patient> found = patientData.findPatient(patientId);
        return found.map(this::toJson)
                .map(ToolResult::new)
                .orElse(new ToolResult("未找到患者：" + patientId));
    }

    private String toJson(Patient patient) {
        try {
            return objectMapper.writeValueAsString(patient);
        } catch (Exception ex) {
            throw new com.aicode.core.domain.exception.ToolExecutionException("failed to serialize patient", ex);
        }
    }
}
