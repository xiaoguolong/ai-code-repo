package com.aicode.framework.multiagent.domain;

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
import com.aicode.framework.multiagent.application.MedicalAssistantRuntimeConfig;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantStep;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 医疗助手 Supervisor Graph 领域服务测试。 */
class MedicalAssistantSupervisorGraphTest {

    private static final String PATIENT_JSON =
            "{\"patientId\":\"P001\",\"name\":\"张三\",\"age\":62,\"gender\":\"male\",\"diagnosis\":\"2 型糖尿病\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PatientRiskToolResultMapper mapper = new PatientRiskToolResultMapper(objectMapper);

    private final PromptTemplatePort promptTemplatePort = new PromptTemplatePort() {
        @Override
        public PromptTemplate load(String name) {
            return new PromptTemplate("v1", "你是" + name + "助手");
        }

        @Override
        public PromptTemplate render(String name, Map<String, Object> variables) {
            return load(name);
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of(
                    new PromptDescriptor("medical-report", "v1"),
                    new PromptDescriptor("medical-followup", "v1"));
        }
    };

    @Test
    void runsSupervisorPipelineForMediumRisk() {
        SequenceChatModelPort chat = new SequenceChatModelPort("分析报告", "随访计划");
        MedicalAssistantSupervisorGraph graph = graph(chat,
                "{\"systolic\":148,\"diastolic\":92,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        MedicalAssistantResult result = graph.run("run-1", "P001", "综合评估");

        assertThat(result.runId()).isEqualTo("run-1");
        assertThat(result.patient().name()).isEqualTo("张三");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.report()).isEqualTo("分析报告");
        assertThat(result.followUpPlan()).isEqualTo("随访计划");
        assertThat(result.usage().totalTokens()).isEqualTo(60);
        assertThat(chat.callCount).isEqualTo(2);

        List<String> agentNames = result.steps().stream().map(MedicalAssistantStep::agentName).toList();
        assertThat(agentNames).containsExactly(
                "supervisor", "data_agent", "supervisor", "analysis_agent", "supervisor",
                "report_agent", "supervisor", "followup_agent", "supervisor");
    }

    @Test
    void propagatesToolExecutionException() {
        ToolPort failing = new ToolPort() {
            @Override
            public List<ToolDefinition> definitions() {
                return List.of();
            }

            @Override
            public ToolResult execute(ToolCall call) {
                throw new ToolExecutionException("boom");
            }
        };
        MedicalAssistantSupervisorGraph graph = new MedicalAssistantSupervisorGraph(
                new SequenceChatModelPort("x", "y"),
                promptTemplatePort,
                failing,
                new PatientRiskAssessor(),
                mapper,
                new MedicalAssistantSupervisor(),
                new MedicalAssistantRuntimeConfig("test-model", 0.0, 128, 12));

        assertThatThrownBy(() -> graph.run("run-2", "P001", "task"))
                .isInstanceOf(ToolExecutionException.class);
    }

    private MedicalAssistantSupervisorGraph graph(ChatModelPort chatModelPort, String metricsJson) {
        return new MedicalAssistantSupervisorGraph(
                chatModelPort,
                promptTemplatePort,
                toolPort(metricsJson),
                new PatientRiskAssessor(),
                mapper,
                new MedicalAssistantSupervisor(),
                new MedicalAssistantRuntimeConfig("test-model", 0.0, 128, 12));
    }

    private ToolPort toolPort(String metricsJson) {
        return new ToolPort() {
            @Override
            public List<ToolDefinition> definitions() {
                return List.of();
            }

            @Override
            public ToolResult execute(ToolCall call) {
                if ("PatientLookupTool".equals(call.name())) {
                    return new ToolResult(PATIENT_JSON);
                }
                if ("HealthMetricTool".equals(call.name())) {
                    return new ToolResult(metricsJson);
                }
                throw new ToolExecutionException("unknown tool: " + call.name());
            }
        };
    }

    private static final class SequenceChatModelPort implements ChatModelPort {

        private final String report;
        private final String followUp;
        private int callCount;

        private SequenceChatModelPort(String report, String followUp) {
            this.report = report;
            this.followUp = followUp;
        }

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            callCount++;
            String content = callCount == 1 ? report : followUp;
            return new ChatResult(content, List.of(), FinishReason.STOP, new TokenUsage(10, 20, 30), "test-model");
        }
    }
}
