package com.aicode.framework.operations.infrastructure;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.operations.domain.OperationsAnomalyDetector;
import com.aicode.framework.operations.domain.OperationsReportComposer;
import com.aicode.framework.operations.domain.OperationsStatistics;
import com.aicode.framework.operations.domain.OperationsThresholds;
import com.aicode.framework.operations.domain.model.OperationsAnalysisRequest;
import com.aicode.framework.operations.domain.model.OperationsAnomalyCode;
import com.aicode.framework.operations.domain.model.OperationsDailyReport;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运营分析 Agent（Week 24）：口径装配、日报生成与两条降级路径。
 */
class OperationsAnalysisAgentTest {

    private static final Instant START = Instant.parse("2026-10-18T09:00:00Z");
    private static final Instant END = Instant.parse("2026-10-25T09:00:00Z");

    private ChatModelPort chatModelPort;
    private PromptTemplatePort promptTemplatePort;
    private OperationsAnalysisAgent agent;

    @BeforeEach
    void setUp() {
        chatModelPort = mock(ChatModelPort.class);
        promptTemplatePort = mock(PromptTemplatePort.class);
        when(promptTemplatePort.load(anyString())).thenReturn(new PromptTemplate("v1", "系统提示"));
        agent = new OperationsAnalysisAgent(
                chatModelPort, promptTemplatePort,
                new OperationsStatistics(), new OperationsAnomalyDetector(),
                new OperationsReportComposer(), ModelPriceCatalog.empty(),
                new OperationsAnalysisRuntimeConfig("test-model", 0.2, 512));
    }

    @Test
    void returnsModelReportWithStatisticsAndAnomalies() {
        when(chatModelPort.chat(anyList(), any(ChatOptions.class))).thenReturn(new ChatResult(
                "## 运营概览\n运行正常", List.of(), FinishReason.STOP,
                new TokenUsage(10, 20, 30), "test-model"));

        OperationsDailyReport report = agent.run(request(records()), OperationsThresholds.defaults());

        assertThat(report.degraded()).isFalse();
        assertThat(report.report()).contains("运营概览");
        assertThat(report.usage().totalTokens()).isEqualTo(30);
        assertThat(report.model()).isEqualTo("test-model");
        assertThat(report.summary().totalRuns()).isEqualTo(11);
        assertThat(report.summary().failureRate()).isEqualTo(0.4);
        assertThat(report.agents()).extracting("agentKey").containsExactly("patient-analysis");
        assertThat(report.agents().get(0).runs()).isEqualTo(11);
        assertThat(report.anomalies()).extracting(anomaly -> anomaly.code().name())
                .contains("FAILURE_RATE_HIGH");
    }

    @Test
    void degradesToRuleTemplateWhenModelFails() {
        when(chatModelPort.chat(anyList(), any(ChatOptions.class)))
                .thenThrow(new ChatModelException("model down"));

        OperationsDailyReport report = agent.run(request(records()), OperationsThresholds.defaults());

        assertThat(report.degraded()).isTrue();
        assertThat(report.report()).contains("规则模板生成").contains("运营概览");
        assertThat(report.usage().totalTokens()).isZero();
        // 统计与异常不因模型失败而缺失
        assertThat(report.summary().totalRuns()).isEqualTo(11);
        assertThat(report.anomalies()).isNotEmpty();
    }

    @Test
    void degradesWhenModelReturnsBlankContent() {
        when(chatModelPort.chat(anyList(), any(ChatOptions.class))).thenReturn(new ChatResult(
                "   ", List.of(), FinishReason.STOP, TokenUsage.unknown(), "test-model"));

        OperationsDailyReport report = agent.run(request(records()), OperationsThresholds.defaults());

        assertThat(report.degraded()).isTrue();
        assertThat(report.report()).contains("规则模板生成");
    }

    @Test
    void degradesWhenPromptTemplateIsMissing() {
        when(promptTemplatePort.load(anyString()))
                .thenThrow(new com.aicode.core.domain.exception.InvalidChatRequestException("not found"));

        OperationsDailyReport report = agent.run(request(records()), OperationsThresholds.defaults());

        assertThat(report.degraded()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void modelPromptContainsOnlyAggregatesAndNeverPatientIds() {
        when(chatModelPort.chat(anyList(), any(ChatOptions.class))).thenReturn(new ChatResult(
                "ok", List.of(), FinishReason.STOP, new TokenUsage(1, 1, 2), "test-model"));

        agent.run(request(records()), OperationsThresholds.defaults());

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModelPort).chat(captor.capture(), any(ChatOptions.class));
        String userMessage = captor.getValue().stream()
                .filter(message -> message.role() == com.aicode.core.domain.model.MessageRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
        assertThat(userMessage).contains("执行：总数 11").doesNotContain("P001", "patientId");
    }

    @Test
    void emptyWindowProducesNoTrafficAnomaly() {
        when(chatModelPort.chat(anyList(), any(ChatOptions.class))).thenReturn(new ChatResult(
                "ok", List.of(), FinishReason.STOP, TokenUsage.unknown(), "test-model"));

        OperationsDailyReport report = agent.run(request(List.of()), OperationsThresholds.defaults());

        assertThat(report.summary().totalRuns()).isZero();
        assertThat(report.anomalies()).extracting(anomaly -> anomaly.code())
                .containsExactly(OperationsAnomalyCode.NO_TRAFFIC);
    }

    private OperationsAnalysisRequest request(List<ExecutionRecord> current) {
        return new OperationsAnalysisRequest(START, END, 7, current, List.of(), 0, false);
    }

    private List<ExecutionRecord> records() {
        List<ExecutionRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            records.add(record("c" + i, ExecutionStatus.COMPLETED, 100, 50));
        }
        for (int i = 0; i < 4; i++) {
            records.add(record("f" + i, ExecutionStatus.FAILED, 0, 0));
        }
        records.add(record("p0", ExecutionStatus.PENDING_APPROVAL, 0, 0));
        return records;
    }

    private ExecutionRecord record(String id, ExecutionStatus status, int prompt, int completion) {
        return new ExecutionRecord(
                id, 2L, "patient-analysis", AgentType.PATIENT_ANALYSIS, status,
                "{\"patientId\":\"P001\"}", "{}", "deepseek-chat",
                new TokenUsage(prompt, completion, prompt + completion), "", START, END);
    }
}
