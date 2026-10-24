package com.aicode.framework.analysis.domain;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.FinishReason;
import com.aicode.core.domain.model.PromptDescriptor;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.analysis.application.PatientAnalysisRuntimeConfig;
import com.aicode.framework.analysis.domain.model.PatientAnalysisResult;
import com.aicode.framework.analysis.domain.model.PatientAnalysisStep;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 患者分析 Agent：工具 → 规则风险评估 → LLM 报告，LLM 失败时降级为规则模板。
 */
class PatientAnalysisAgentTest {

    private static final PatientAnalysisRuntimeConfig CONFIG =
            new PatientAnalysisRuntimeConfig("test-model", 0.2, 512);

    @Test
    void runsFullChainAndReturnsStructuredResult() {
        PatientAnalysisAgent agent = agent(new FakeToolPort(), chatAnswer("模型生成的报告正文"));

        PatientAnalysisResult result = agent.run("P001", "生成慢病分析报告");

        assertThat(result.patientId()).isEqualTo("P001");
        assertThat(result.patient().name()).isEqualTo("张三");
        assertThat(result.reports()).hasSize(2);
        assertThat(result.metrics().hba1c()).isEqualTo(7.9);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.report()).isEqualTo("模型生成的报告正文");
        assertThat(result.degraded()).isFalse();
        assertThat(result.memoryId()).isNull();
        assertThat(result.usage().totalTokens()).isEqualTo(33);
        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.steps())
                .extracting(PatientAnalysisStep::agent)
                .containsExactly("patient_lookup", "report_lookup", "metric_lookup", "risk_analysis", "report_generation");
        assertThat(result.steps()).extracting(PatientAnalysisStep::stepNo).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void degradesToRuleTemplateWhenModelFails() {
        ChatModelPort failing = (messages, options, tools) -> {
            throw new ChatModelException("model timeout");
        };
        PatientAnalysisAgent agent = agent(new FakeToolPort(), failing);

        PatientAnalysisResult result = agent.run("P001", "生成慢病分析报告");

        assertThat(result.degraded()).isTrue();
        assertThat(result.report()).contains("模型不可用").contains("中风险");
        assertThat(result.usage().totalTokens()).isZero();
        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.steps()).hasSize(5);
    }

    @Test
    void propagatesToolFailureInsteadOfSwallowingIt() {
        ToolPort failing = new FakeToolPort() {
            @Override
            public ToolResult execute(ToolCall call) {
                throw new ToolExecutionException("tool down");
            }
        };

        assertThatThrownBy(() -> agent(failing, chatAnswer("x")).run("P001", "task"))
                .isInstanceOf(ToolExecutionException.class);
    }

    private PatientAnalysisAgent agent(ToolPort toolPort, ChatModelPort chatModelPort) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new PatientAnalysisAgent(
                toolPort,
                chatModelPort,
                new StubPromptTemplatePort(),
                new PatientRiskAssessor(),
                new PatientRiskToolResultMapper(objectMapper),
                objectMapper,
                CONFIG);
    }

    private ChatModelPort chatAnswer(String content) {
        return (messages, options, tools) ->
                new ChatResult(content, List.of(), FinishReason.STOP, new TokenUsage(11, 22, 33), "test-model");
    }

    /** 工具桩：返回与真实工具同构的 JSON。 */
    private static class FakeToolPort implements ToolPort {

        @Override
        public List<ToolDefinition> definitions() {
            return List.of();
        }

        @Override
        public ToolResult execute(ToolCall call) {
            return switch (call.name()) {
                case "PatientLookupTool" -> new ToolResult(
                        "{\"patientId\":\"P001\",\"name\":\"张三\",\"age\":62,\"gender\":\"male\","
                                + "\"diagnosis\":\"2 型糖尿病\"}");
                case "ReportLookupTool" -> new ToolResult(
                        "{\"patientId\":\"P001\",\"reports\":["
                                + "{\"reportId\":\"R001\",\"type\":\"糖化血红蛋白\",\"date\":\"2026-09-12\","
                                + "\"conclusion\":\"HbA1c 8.1%\",\"abnormal\":true},"
                                + "{\"reportId\":\"R002\",\"type\":\"肾功能\",\"date\":\"2026-09-20\","
                                + "\"conclusion\":\"eGFR 78\",\"abnormal\":false}]}");
                case "HealthMetricTool" -> new ToolResult(
                        "{\"patientId\":\"P001\",\"systolic\":148,\"diastolic\":92,"
                                + "\"fastingGlucose\":8.6,\"hba1c\":7.9}");
                default -> throw new ToolExecutionException("unknown tool: " + call.name());
            };
        }
    }

    /** Prompt 桩：不读 classpath，避免测试依赖资源文件。 */
    private static final class StubPromptTemplatePort implements PromptTemplatePort {

        @Override
        public PromptTemplate load(String name) {
            return new PromptTemplate("v1", "你是患者分析助手，只依据给定数据生成报告。");
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of();
        }

        @Override
        public PromptTemplate render(String name, Map<String, Object> variables) {
            return load(name);
        }
    }
}
