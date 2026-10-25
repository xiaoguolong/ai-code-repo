package com.aicode.framework.followup.infrastructure;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.ToolExecutionException;
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
import com.aicode.framework.followup.domain.FollowUpPlanner;
import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 随访 Agent（Week 24）：规则任务不被模型改动、两条降级路径、工具失败不吞错。
 */
class FollowUpAgentTest {

    private static final Instant NOW = Instant.parse("2026-10-25T09:00:00Z");
    private static final FollowUpRuntimeConfig CONFIG = new FollowUpRuntimeConfig("test-model", 0.2, 512);

    @Test
    void highRiskPatientGetsThreeRuleTasksAndModelText() {
        FollowUpAgent agent = agent(new FakeToolPort(), chatAnswer("## 随访安排\n3 天后复测血压"));

        FollowUpPlanResult result = agent.run("P002", "生成随访计划");

        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.tasks()).hasSize(3);
        assertThat(result.degraded()).isFalse();
        assertThat(result.planText()).contains("随访安排");
        assertThat(result.usage().totalTokens()).isEqualTo(33);
        assertThat(result.steps()).extracting("agent")
                .containsExactly("patient_lookup", "metric_lookup", "risk_analysis", "plan_generation");
    }

    @Test
    void mediumRiskPatientGetsTwoTasks() {
        FollowUpAgent agent = agent(new MediumRiskToolPort(), chatAnswer("ok"));

        FollowUpPlanResult result = agent.run("P003", null);

        assertThat(result.riskLevel()).isEqualTo("MEDIUM");
        assertThat(result.tasks()).hasSize(2);
    }

    @Test
    void modelFailureDegradesToRuleTextButKeepsTasks() {
        ChatModelPort failing = (messages, options, tools) -> {
            throw new ChatModelException("model down");
        };

        FollowUpPlanResult result = agent(new FakeToolPort(), failing).run("P002", null);

        assertThat(result.degraded()).isTrue();
        assertThat(result.planText()).contains("规则模板生成").contains("随访计划");
        assertThat(result.tasks()).hasSize(3);
        assertThat(result.usage().totalTokens()).isZero();
    }

    @Test
    void blankModelContentDegrades() {
        FollowUpPlanResult result = agent(new FakeToolPort(), chatAnswer("  ")).run("P002", null);

        assertThat(result.degraded()).isTrue();
        assertThat(result.tasks()).hasSize(3);
    }

    @Test
    void toolFailureIsPropagated() {
        ToolPort failing = new ToolPort() {
            @Override
            public List<ToolDefinition> definitions() {
                return List.of();
            }

            @Override
            public ToolResult execute(ToolCall call) {
                throw new ToolExecutionException("tool down");
            }
        };

        assertThatThrownBy(() -> agent(failing, chatAnswer("x")).run("P002", null))
                .isInstanceOf(ToolExecutionException.class);
    }

    private FollowUpAgent agent(ToolPort toolPort, ChatModelPort chatModelPort) {
        return new FollowUpAgent(
                toolPort, chatModelPort, new StubPromptTemplatePort(),
                new PatientRiskAssessor(), new PatientRiskToolResultMapper(new ObjectMapper()),
                new FollowUpPlanner(), CONFIG, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ChatModelPort chatAnswer(String content) {
        return (messages, options, tools) -> new ChatResult(
                content, List.of(), FinishReason.STOP, new TokenUsage(11, 22, 33), "test-model");
    }

    /** HIGH 风险患者（收缩压 168 / HbA1c 9.6）。 */
    private static class FakeToolPort implements ToolPort {

        @Override
        public List<ToolDefinition> definitions() {
            return List.of();
        }

        @Override
        public ToolResult execute(ToolCall call) {
            return switch (call.name()) {
                case "PatientLookupTool" -> new ToolResult(
                        "{\"patientId\":\"P002\",\"name\":\"李四\",\"age\":70,\"gender\":\"female\","
                                + "\"diagnosis\":\"2 型糖尿病\"}");
                case "HealthMetricTool" -> new ToolResult(
                        "{\"patientId\":\"P002\",\"systolic\":168,\"diastolic\":104,"
                                + "\"fastingGlucose\":12.3,\"hba1c\":9.6}");
                default -> throw new ToolExecutionException("unknown tool: " + call.name());
            };
        }
    }

    /** MEDIUM 风险患者（收缩压 148 / HbA1c 7.9）。 */
    private static final class MediumRiskToolPort extends FakeToolPort {

        @Override
        public ToolResult execute(ToolCall call) {
            if ("HealthMetricTool".equals(call.name())) {
                return new ToolResult(
                        "{\"patientId\":\"P003\",\"systolic\":148,\"diastolic\":92,"
                                + "\"fastingGlucose\":8.6,\"hba1c\":7.9}");
            }
            return new ToolResult(
                    "{\"patientId\":\"P003\",\"name\":\"王五\",\"age\":55,\"gender\":\"male\","
                            + "\"diagnosis\":\"高血压\"}");
        }
    }

    /** Prompt 桩：不读 classpath。 */
    private static final class StubPromptTemplatePort implements PromptTemplatePort {

        @Override
        public PromptTemplate load(String name) {
            return new PromptTemplate("v1", "你是随访助手，不得改动已确定的任务。");
        }

        @Override
        public PromptTemplate render(String name, java.util.Map<String, Object> variables) {
            return load(name);
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of();
        }
    }
}
