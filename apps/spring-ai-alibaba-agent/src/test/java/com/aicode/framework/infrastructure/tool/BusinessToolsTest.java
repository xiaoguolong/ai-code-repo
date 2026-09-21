package com.aicode.framework.infrastructure.tool;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 演示工具测试：定义完整、正常执行、缺参报错。
 */
class BusinessToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void patientLookupReturnsCannedPatient() {
        PatientLookupTool tool = new PatientLookupTool(objectMapper);

        assertThat(tool.definition().name()).isEqualTo("PatientLookupTool");
        assertThat(tool.definition().parameters()).containsKey("properties");

        ToolResult result = tool.execute(Map.of("patientId", "P001"));
        assertThat(result.output()).contains("P001").contains("张三");
    }

    @Test
    void healthMetricReturnsCannedMetrics() {
        HealthMetricTool tool = new HealthMetricTool(objectMapper);

        assertThat(tool.definition().name()).isEqualTo("HealthMetricTool");

        ToolResult result = tool.execute(Map.of("patientId", "P001"));
        assertThat(result.output()).contains("systolic").contains("148");
    }

    @Test
    void toolsRejectMissingPatientId() {
        PatientLookupTool tool = new PatientLookupTool(objectMapper);

        assertThatThrownBy(() -> tool.execute(Map.of()))
                .isInstanceOf(ToolExecutionException.class);
    }
}
