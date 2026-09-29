package com.aicode.framework.workflow.domain;

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
import com.aicode.framework.workflow.application.PatientRiskRuntimeConfig;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 患者风险分析 Workflow 领域服务测试：用 fake ToolPort + 脚本化 ChatModelPort 驱动固定流程，
 * 不依赖真实网络。验证固定顺序、条件边（HIGH→escalate）、报告生成与异常解包。
 */
class PatientRiskWorkflowTest {

    private static final String PATIENT_JSON =
            "{\"patientId\":\"P001\",\"name\":\"张三\",\"age\":62,\"gender\":\"male\",\"diagnosis\":\"2 型糖尿病\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PatientRiskToolResultMapper mapper = new PatientRiskToolResultMapper(objectMapper);

    private final PromptTemplatePort promptTemplatePort = new PromptTemplatePort() {
        @Override
        public PromptTemplate load(String name) {
            return new PromptTemplate("v1", "你是患者风险分析助手");
        }

        @Override
        public PromptTemplate render(String name, Map<String, Object> variables) {
            return load(name);
        }

        @Override
        public List<PromptDescriptor> list() {
            return List.of(new PromptDescriptor("patient-risk-report", "v1"));
        }
    };

    @Test
    void runsRoutineReportForMediumRisk() {
        CapturingChatModelPort chat = new CapturingChatModelPort("中风险报告");
        PatientRiskWorkflow workflow = workflow(chat,
                "{\"systolic\":148,\"diastolic\":92,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        PatientRiskWorkflowResult result = workflow.run("wf-1", "P001");

        assertThat(result.workflowId()).isEqualTo("wf-1");
        assertThat(result.patientId()).isEqualTo("P001");
        assertThat(result.patient().name()).isEqualTo("张三");
        assertThat(result.metrics().hba1c()).isEqualTo(7.9);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.escalated()).isFalse();
        assertThat(result.report()).isEqualTo("中风险报告");
        assertThat(result.usage().totalTokens()).isEqualTo(30);
        assertThat(result.model()).isEqualTo("test-model");
    }

    @Test
    void runsEscalatedReportForHighRisk() {
        CapturingChatModelPort chat = new CapturingChatModelPort("高风险报告");
        PatientRiskWorkflow workflow = workflow(chat,
                "{\"systolic\":162,\"diastolic\":98,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        PatientRiskWorkflowResult result = workflow.run("wf-2", "P001");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.escalated()).isTrue();
        assertThat(result.report()).isEqualTo("高风险报告");
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
        PatientRiskWorkflow workflow = new PatientRiskWorkflow(
                new CapturingChatModelPort("x"), promptTemplatePort, failing,
                new PatientRiskAssessor(), mapper,
                new PatientRiskRuntimeConfig("test-model", 0.0, 128));

        assertThatThrownBy(() -> workflow.run("wf-3", "P001"))
                .isInstanceOf(ToolExecutionException.class);
    }

    private PatientRiskWorkflow workflow(ChatModelPort chatModelPort, String metricsJson) {
        return new PatientRiskWorkflow(
                chatModelPort,
                promptTemplatePort,
                toolPort(metricsJson),
                new PatientRiskAssessor(),
                mapper,
                new PatientRiskRuntimeConfig("test-model", 0.0, 128));
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

    /**
     * 脚本化模型端口：记录最后一次调用的消息并返回固定报告。
     */
    private static final class CapturingChatModelPort implements ChatModelPort {

        private final String report;
        private List<ChatMessage> lastMessages = List.of();

        private CapturingChatModelPort(String report) {
            this.report = report;
        }

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            this.lastMessages = messages;
            return new ChatResult(report, List.of(), FinishReason.STOP, new TokenUsage(10, 20, 30), "test-model");
        }
    }
}
