package com.aicode.patient.infrastructure.tool;

import com.aicode.core.domain.model.ToolResult;
import com.aicode.patient.infrastructure.data.InMemoryPatientDataAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 患者工具单元测试：验证工具定义、查询命中与未命中。
 */
class PatientToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PatientTool tool = new PatientTool(new InMemoryPatientDataAdapter(), objectMapper);

    @Test
    void definitionHasNameAndParameters() {
        assertThat(tool.definition().name()).isEqualTo("PatientTool");
        assertThat(tool.definition().parameters()).containsKey("properties");
    }

    @Test
    void returnsPatientJsonForKnownId() {
        ToolResult result = tool.execute(Map.of("patientId", "P001"));

        assertThat(result.output()).contains("张三").contains("P001");
    }

    @Test
    void returnsNotFoundForUnknownId() {
        ToolResult result = tool.execute(Map.of("patientId", "P999"));

        assertThat(result.output()).contains("未找到患者");
    }
}
