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
import com.aicode.framework.workflow.domain.exception.WorkflowNotFoundException;
import com.aicode.framework.workflow.domain.exception.WorkflowNotPendingException;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.aicode.framework.workflow.domain.model.WorkflowStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 患者风险分析 Workflow 领域服务测试：固定流程、HITL 暂停/恢复、异常解包。
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

        assertThat(result.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(result.workflowId()).isEqualTo("wf-1");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.escalated()).isFalse();
        assertThat(result.report()).isEqualTo("中风险报告");
        assertThat(result.usage().totalTokens()).isEqualTo(30);
    }

    @Test
    void pausesHighRiskForHumanReview() {
        CapturingChatModelPort chat = new CapturingChatModelPort("高风险报告");
        PatientRiskWorkflow workflow = workflow(chat,
                "{\"systolic\":162,\"diastolic\":98,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        PatientRiskWorkflowResult pending = workflow.start("wf-2", "P001");

        assertThat(pending.status()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(pending.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(pending.escalated()).isFalse();
        assertThat(pending.report()).isEmpty();
        assertThat(chat.callCount).isZero();
    }

    @Test
    void resumesApprovedHighRiskToCompletedReport() {
        CapturingChatModelPort chat = new CapturingChatModelPort("高风险报告");
        PatientRiskWorkflow workflow = workflow(chat,
                "{\"systolic\":162,\"diastolic\":98,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        workflow.start("wf-3", "P001");
        PatientRiskWorkflowResult completed = workflow.resume("wf-3", true);

        assertThat(completed.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(completed.escalated()).isTrue();
        assertThat(completed.report()).isEqualTo("高风险报告");
        assertThat(chat.callCount).isEqualTo(1);
    }

    @Test
    void resumesRejectedHighRiskWithoutReport() {
        CapturingChatModelPort chat = new CapturingChatModelPort("不应调用");
        PatientRiskWorkflow workflow = workflow(chat,
                "{\"systolic\":162,\"diastolic\":98,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        workflow.start("wf-4", "P001");
        PatientRiskWorkflowResult rejected = workflow.resume("wf-4", false);

        assertThat(rejected.status()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(rejected.report()).isEmpty();
        assertThat(rejected.escalated()).isFalse();
        assertThat(chat.callCount).isZero();
    }

    @Test
    void getRunReturnsPendingSnapshot() {
        PatientRiskWorkflow workflow = workflow(new CapturingChatModelPort("x"),
                "{\"systolic\":162,\"diastolic\":98,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        workflow.start("wf-5", "P001");
        PatientRiskWorkflowResult snapshot = workflow.getRun("wf-5");

        assertThat(snapshot.status()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(snapshot.patient().name()).isEqualTo("张三");
    }

    @Test
    void resumeFailsWhenNotPending() {
        PatientRiskWorkflow workflow = workflow(new CapturingChatModelPort("中风险报告"),
                "{\"systolic\":148,\"diastolic\":92,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        workflow.start("wf-6", "P001");

        assertThatThrownBy(() -> workflow.resume("wf-6", true))
                .isInstanceOf(WorkflowNotPendingException.class);
    }

    @Test
    void getRunFailsWhenUnknownWorkflow() {
        PatientRiskWorkflow workflow = workflow(new CapturingChatModelPort("x"),
                "{\"systolic\":148,\"diastolic\":92,\"fastingGlucose\":8.6,\"hba1c\":7.9}");

        assertThatThrownBy(() -> workflow.getRun("unknown"))
                .isInstanceOf(WorkflowNotFoundException.class);
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

        assertThatThrownBy(() -> workflow.run("wf-7", "P001"))
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

    private static final class CapturingChatModelPort implements ChatModelPort {

        private final String report;
        private int callCount;

        private CapturingChatModelPort(String report) {
            this.report = report;
        }

        @Override
        public ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools) {
            callCount++;
            return new ChatResult(report, List.of(), FinishReason.STOP, new TokenUsage(10, 20, 30), "test-model");
        }
    }
}
