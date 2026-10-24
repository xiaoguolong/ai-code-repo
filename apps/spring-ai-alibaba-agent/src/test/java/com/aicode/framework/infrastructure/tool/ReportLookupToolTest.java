package com.aicode.framework.infrastructure.tool;

import com.aicode.core.domain.exception.ToolExecutionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 检查报告工具契约：确定性演示数据、缺参报错。 */
class ReportLookupToolTest {

    private final ReportLookupTool tool = new ReportLookupTool(new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void returnsDeterministicReportsForPatient() {
        String output = tool.execute(Map.of("patientId", "P001")).output();

        assertThat(output)
                .contains("\"patientId\":\"P001\"")
                .contains("\"reports\"")
                .contains("R001")
                .contains("R002");

        assertThat(tool.execute(Map.of("patientId", "P001")).output()).isEqualTo(output);
    }

    @Test
    void rejectsMissingPatientId() {
        assertThatThrownBy(() -> tool.execute(Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("patientId");
    }

    @Test
    void exposesMcpCompatibleDefinition() {
        assertThat(tool.definition().name()).isEqualTo("ReportLookupTool");
        assertThat(tool.definition().description()).isNotBlank();
    }
}
