package com.aicode.patient.infrastructure.tool;

import com.aicode.core.domain.model.ToolResult;
import com.aicode.patient.infrastructure.data.InMemoryPatientDataAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报告 / 健康指标 / 医嘱工具单元测试：验证各工具返回对应业务数据。
 */
class BusinessToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InMemoryPatientDataAdapter data = new InMemoryPatientDataAdapter();
    private final ReportTool reportTool = new ReportTool(data, objectMapper);
    private final HealthDataTool healthDataTool = new HealthDataTool(data, objectMapper);
    private final OrderTool orderTool = new OrderTool(data, objectMapper);

    @Test
    void reportToolReturnsReports() {
        ToolResult result = reportTool.execute(Map.of("patientId", "P001"));

        assertThat(result.output()).contains("血常规").contains("心电图");
    }

    @Test
    void healthDataToolReturnsMetrics() {
        ToolResult result = healthDataTool.execute(Map.of("patientId", "P001"));

        assertThat(result.output()).contains("收缩压").contains("空腹血糖");
    }

    @Test
    void orderToolReturnsOrders() {
        ToolResult result = orderTool.execute(Map.of("patientId", "P001"));

        assertThat(result.output()).contains("硝苯地平缓释片").contains("二甲双胍");
    }

    @Test
    void returnsEmptyListForPatientWithoutData() {
        assertThat(reportTool.execute(Map.of("patientId", "P999")).output()).isEqualTo("[]");
        assertThat(orderTool.execute(Map.of("patientId", "P999")).output()).isEqualTo("[]");
    }
}
