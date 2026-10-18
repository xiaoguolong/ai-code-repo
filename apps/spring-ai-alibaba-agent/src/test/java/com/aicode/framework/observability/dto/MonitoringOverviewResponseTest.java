package com.aicode.framework.observability.dto;

import com.aicode.framework.observability.domain.MonitoringComponent;
import com.aicode.framework.observability.domain.MonitoringOverviewView;
import com.aicode.framework.observability.domain.MonitoringStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 监控概览响应体测试（Week 20）。
 *
 * <p>对外 JSON 是稳定契约：状态用字符串（不暴露枚举序列化细节）、字段名固定、
 * 且<b>禁止出现任何密钥字段</b>。仪表盘与运维脚本都按这里的字段名取值。</p>
 */
class MonitoringOverviewResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("视图 → DTO：状态改字符串，allHealthy 透传，组件顺序不变")
    void mapsViewToResponse() {
        MonitoringOverviewView view = new MonitoringOverviewView(
                Instant.parse("2026-10-18T12:00:00Z"),
                List.of(
                        new MonitoringComponent("otel", "OpenTelemetry", true, MonitoringStatus.UP,
                                "http://collector:4318/v1/traces", "sampling=1.0", ""),
                        new MonitoringComponent("grafana", "Grafana", false, MonitoringStatus.DISABLED,
                                "", "monitoring.enabled=false", "")),
                new MonitoringOverviewView.Correlation(
                        "week20-e2e", "otel-trace", "aicode.trace_id",
                        "http://langfuse.local:3000/trace/otel-trace",
                        "http://127.0.0.1:3001/d/aicode-platform-overview",
                        "http://127.0.0.1:8088",
                        Map.of("logs", "traceId=week20-e2e")));

        MonitoringOverviewResponse response = MonitoringOverviewResponse.from(view);

        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-10-18T12:00:00Z"));
        assertThat(response.allHealthy()).isTrue();
        assertThat(response.components()).hasSize(2);
        assertThat(response.components().get(0).status()).isEqualTo("UP");
        assertThat(response.components().get(1).status()).isEqualTo("DISABLED");
        assertThat(response.correlation().correlationTag()).isEqualTo("aicode.trace_id");
        assertThat(response.correlation().queryHints()).containsEntry("logs", "traceId=week20-e2e");
    }

    @Test
    @DisplayName("JSON 字段名稳定：data 里读 components / allHealthy / correlation.queryHints")
    void jsonFieldNamesAreStable() throws Exception {
        MonitoringOverviewResponse response = MonitoringOverviewResponse.from(new MonitoringOverviewView(
                Instant.parse("2026-10-18T12:00:00Z"),
                List.of(new MonitoringComponent("otel", "OpenTelemetry", true, MonitoringStatus.UP, "", "", "")),
                new MonitoringOverviewView.Correlation("t", "o", "aicode.trace_id", "", "", "", Map.of())));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"allHealthy\":true");
        assertThat(json).contains("\"components\":[");
        assertThat(json).contains("\"status\":\"UP\"");
        assertThat(json).contains("\"queryHints\":{");
    }

    @Test
    @DisplayName("响应体不含密钥字段（类型上就没有，序列化后再兜底断言一次）")
    void responseNeverCarriesSecrets() throws Exception {
        MonitoringOverviewResponse response = MonitoringOverviewResponse.from(new MonitoringOverviewView(
                Instant.now(),
                List.of(new MonitoringComponent("langfuse", "Langfuse", true, MonitoringStatus.UP,
                        "http://langfuse.local:3000", "prompt 管理已启用", "http://langfuse.local:3000")),
                new MonitoringOverviewView.Correlation("t", "o", "aicode.trace_id", "", "", "", Map.of())));

        String json = objectMapper.writeValueAsString(response).toLowerCase();

        assertThat(json).doesNotContain("secret", "password", "api-key", "apikey", "token\"");
    }

    @Test
    @DisplayName("没有组件时 allHealthy=false（『什么都没配』不能算健康）")
    void emptyOverviewIsNotHealthy() {
        MonitoringOverviewView view = new MonitoringOverviewView(
                Instant.now(), List.of(),
                new MonitoringOverviewView.Correlation("", "", "aicode.trace_id", "", "", "", Map.of()));

        assertThat(view.allHealthy()).isFalse();
        assertThat(MonitoringOverviewResponse.from(view).components()).isEmpty();
    }
}
