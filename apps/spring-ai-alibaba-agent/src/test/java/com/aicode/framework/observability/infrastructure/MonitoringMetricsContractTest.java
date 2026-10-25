package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.MonitoringMetricNames;
import com.aicode.framework.observability.domain.ObservabilityAttributes;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 指标名与标签契约测试（Week 20）。
 *
 * <p>Grafana 面板与 Prometheus 告警是按<b>导出名</b>写死的：{@code llm.tokens.total} 经 Micrometer
 * 导出后是 {@code llm_tokens_total}，{@code agent.run.count} 是 {@code agent_run_count_total}。
 * 一旦有人改常量或改标签键，仪表盘会静默变成空图——所以这里用真实 Prometheus 注册表
 * 抓一次文本格式，把名字与标签锁死。</p>
 */
class MonitoringMetricsContractTest {

    @Test
    @DisplayName("计数器的 Prometheus 导出名与标签")
    void counterNamesAndLabels() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerObservabilityAdapter adapter = new MicrometerObservabilityAdapter(mock(Tracer.class), registry);

        adapter.recordCounter(MonitoringMetricNames.AGENT_RUN_COUNT, 1.0,
                ObservabilityAttributes.AGENT_KEY, "medical-assistant",
                ObservabilityAttributes.EXECUTION_STATUS, "COMPLETED");
        adapter.recordCounter(MonitoringMetricNames.LLM_CALL_COUNT, 1.0,
                ObservabilityAttributes.GEN_AI_REQUEST_MODEL, "deepseek-v4-pro",
                ObservabilityAttributes.LLM_OUTCOME, "SUCCESS");
        adapter.recordCounter(MonitoringMetricNames.LLM_TOKENS, 120,
                ObservabilityAttributes.GEN_AI_REQUEST_MODEL, "deepseek-v4-pro",
                ObservabilityAttributes.TOKEN_TYPE, "prompt");
        adapter.recordCounter(MonitoringMetricNames.LLM_COST_USD, 0.0003,
                ObservabilityAttributes.GEN_AI_REQUEST_MODEL, "deepseek-v4-pro");
        adapter.recordCounter(MonitoringMetricNames.TOOL_CALL_COUNT, 1.0,
                ObservabilityAttributes.TOOL_NAME, "PatientLookupTool",
                ObservabilityAttributes.TOOL_OUTCOME, "SUCCESS");

        String scrape = registry.scrape();

        assertThat(scrape).contains("agent_run_count_total{");
        assertThat(scrape).contains("agent_key=\"medical-assistant\"");
        assertThat(scrape).contains("execution_status=\"COMPLETED\"");
        assertThat(scrape).contains("llm_call_count_total{");
        assertThat(scrape).contains("gen_ai_request_model=\"deepseek-v4-pro\"");
        assertThat(scrape).contains("llm_tokens_total{");
        assertThat(scrape).contains("token_type=\"prompt\"");
        assertThat(scrape).contains("llm_cost_usd_total{");
        assertThat(scrape).contains("tool_call_count_total{");
        assertThat(scrape).contains("tool_name=\"PatientLookupTool\"");
    }

    @Test
    @DisplayName("Timer 的导出名带 _seconds 后缀（告警 histogram_quantile 依赖它）")
    void timerNamesCarrySecondsSuffix() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerObservabilityAdapter adapter = new MicrometerObservabilityAdapter(mock(Tracer.class), registry);

        adapter.recordDuration(MonitoringMetricNames.AGENT_RUN_DURATION, 1234L,
                ObservabilityAttributes.AGENT_KEY, "medical-assistant",
                ObservabilityAttributes.EXECUTION_STATUS, "COMPLETED");
        adapter.recordDuration(MonitoringMetricNames.LLM_CALL_DURATION, 800L,
                ObservabilityAttributes.GEN_AI_REQUEST_MODEL, "deepseek-v4-pro",
                ObservabilityAttributes.LLM_OUTCOME, "SUCCESS");

        String scrape = registry.scrape();

        assertThat(scrape).contains("agent_run_duration_seconds_count{");
        assertThat(scrape).contains("agent_run_duration_seconds_sum{");
        assertThat(scrape).contains("llm_call_duration_seconds_count{");
    }

    @Test
    @DisplayName("指标名常量不允许出现大写或空格（Prometheus 名称规范）")
    void metricNamesArePrometheusSafe() {
        for (String name : MonitoringMetricNames.all()) {
            assertThat(name).matches("[a-z][a-z0-9]*\\.[a-z0-9.]+");
        }
        assertThat(MonitoringMetricNames.all()).contains(
                MonitoringMetricNames.AGENT_RUN_COUNT,
                MonitoringMetricNames.AGENT_RUN_DURATION,
                MonitoringMetricNames.LLM_CALL_COUNT,
                MonitoringMetricNames.LLM_CALL_DURATION,
                MonitoringMetricNames.LLM_TOKENS,
                MonitoringMetricNames.LLM_COST_USD,
                MonitoringMetricNames.TOOL_CALL_COUNT,
                MonitoringMetricNames.APPROVAL_PENDING_COUNT,
                MonitoringMetricNames.APPROVAL_REMINDER_COUNT,
                MonitoringMetricNames.KNOWLEDGE_SEARCH_DURATION,
                MonitoringMetricNames.KNOWLEDGE_HIT_COUNT,
                MonitoringMetricNames.KNOWLEDGE_RECONCILE_COUNT,
                MonitoringMetricNames.KNOWLEDGE_BINDING_MISSING_COUNT);
    }

    @Test
    @DisplayName("Week 27 知识库/提醒指标的导出名（Grafana 面板依赖）")
    void knowledgeAndReminderMetricsExportNames() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerObservabilityAdapter adapter = new MicrometerObservabilityAdapter(mock(Tracer.class), registry);

        adapter.recordDuration(MonitoringMetricNames.KNOWLEDGE_SEARCH_DURATION, 120L,
                ObservabilityAttributes.KNOWLEDGE_OUTCOME, "SUCCESS");
        adapter.recordCounter(MonitoringMetricNames.KNOWLEDGE_HIT_COUNT, 2.0);
        adapter.recordCounter(MonitoringMetricNames.KNOWLEDGE_RECONCILE_COUNT, 1.0,
                ObservabilityAttributes.KNOWLEDGE_RESULT, "missing");
        adapter.recordGauge(MonitoringMetricNames.KNOWLEDGE_BINDING_MISSING_COUNT, 1.0);
        adapter.recordCounter(MonitoringMetricNames.APPROVAL_REMINDER_COUNT, 1.0);

        String scrape = registry.scrape();

        assertThat(scrape).contains("knowledge_search_duration_seconds_count{");
        assertThat(scrape).contains("knowledge_outcome=\"SUCCESS\"");
        assertThat(scrape).contains("knowledge_hit_count_total 2.0");
        assertThat(scrape).contains("knowledge_reconcile_count_total{");
        assertThat(scrape).contains("knowledge_result=\"missing\"");
        assertThat(scrape).contains("knowledge_binding_missing_count 1.0");
        assertThat(scrape).contains("approval_reminder_count_total 1.0");
    }

    @Test
    @DisplayName("Gauge 的导出名与最新值（待审积压告警依赖它）")
    void gaugeExportsLatestValueWithoutSuffix() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerObservabilityAdapter adapter = new MicrometerObservabilityAdapter(mock(Tracer.class), registry);

        adapter.recordGauge(MonitoringMetricNames.APPROVAL_PENDING_COUNT, 3.0);
        assertThat(registry.scrape()).contains("approval_pending_count 3.0");

        adapter.recordGauge(MonitoringMetricNames.APPROVAL_PENDING_COUNT, 21.0);
        String scrape = registry.scrape();
        assertThat(scrape).contains("approval_pending_count 21.0");
        assertThat(scrape).doesNotContain("approval_pending_count 3.0");
        assertThat(scrape).contains("# TYPE approval_pending_count gauge");
    }

    @Test
    @DisplayName("关闭观测时不登记任何指标（空实现语义不回退）")
    void noopAdapterRecordsNothing() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        com.aicode.framework.observability.domain.AgentObservabilityPort noop =
                new com.aicode.framework.observability.domain.NoopObservabilityAdapter();

        noop.recordCounter(MonitoringMetricNames.AGENT_RUN_COUNT, 1.0);
        noop.recordDuration(MonitoringMetricNames.AGENT_RUN_DURATION, 10L);

        assertThat(registry.scrape()).doesNotContain("agent_run");
    }
}
