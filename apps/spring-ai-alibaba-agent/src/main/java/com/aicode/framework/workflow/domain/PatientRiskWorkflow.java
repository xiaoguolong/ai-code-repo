package com.aicode.framework.workflow.domain;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.workflow.application.PatientRiskRuntimeConfig;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskAssessment;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 患者风险分析 Workflow（领域服务）。用 Spring AI Alibaba Graph 编排固定多节点流程：
 * {@code START → query_patient → query_metrics → judge_risk →(条件边)→ escalate/generate_report → generate_report → END}。
 * <p>
 * 与第 8 周「模型带工具循环」不同，节点按固定顺序执行，工具由节点直接经 {@link ToolPort} 调度；
 * 风险判断为确定性规则（{@link PatientRiskAssessor}）；报告由 {@link ChatModelPort} 生成。
 */
public class PatientRiskWorkflow {

    private static final String NODE_QUERY_PATIENT = "query_patient";
    private static final String NODE_QUERY_METRICS = "query_metrics";
    private static final String NODE_JUDGE_RISK = "judge_risk";
    private static final String NODE_ESCALATE = "escalate";
    private static final String NODE_REPORT = "generate_report";

    private static final String ROUTE_ROUTINE = "routine";
    private static final String ROUTE_URGENT = "urgent";

    private static final String TOOL_PATIENT = "PatientLookupTool";
    private static final String TOOL_METRICS = "HealthMetricTool";

    static final String KEY_PATIENT_ID = "patientId";
    static final String KEY_PATIENT = "patient";
    static final String KEY_METRICS = "metrics";
    static final String KEY_RISK_LEVEL = "riskLevel";
    static final String KEY_JUSTIFICATION = "justification";
    static final String KEY_ROUTE = "route";
    static final String KEY_ESCALATED = "escalated";
    static final String KEY_GUIDANCE = "guidance";
    static final String KEY_REPORT = "report";
    static final String KEY_USAGE = "usage";
    static final String KEY_MODEL = "model";

    private static final String ESCALATION_GUIDANCE = "该患者为高风险，建议立即转诊内分泌科并安排门诊或住院评估。";

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ToolPort toolPort;
    private final PatientRiskAssessor riskAssessor;
    private final PatientRiskToolResultMapper toolResultMapper;
    private final PatientRiskRuntimeConfig config;
    private final CompiledGraph graph;

    public PatientRiskWorkflow(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ToolPort toolPort,
            PatientRiskAssessor riskAssessor,
            PatientRiskToolResultMapper toolResultMapper,
            PatientRiskRuntimeConfig config
    ) {
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.toolPort = toolPort;
        this.riskAssessor = riskAssessor;
        this.toolResultMapper = toolResultMapper;
        this.config = config;
        this.graph = buildGraph();
    }

    /**
     * 执行一次患者风险分析流程。
     *
     * @param workflowId 流程标识
     * @param patientId  患者编号
     * @return 患者/指标/风险/报告汇总结果
     * @throws ToolExecutionException 工具执行失败
     * @throws ChatModelException     报告生成时模型调用失败
     */
    public PatientRiskWorkflowResult run(String workflowId, String patientId) {
        Map<String, Object> input = new HashMap<>();
        input.put(KEY_PATIENT_ID, patientId);
        input.put(KEY_ROUTE, ROUTE_ROUTINE);
        input.put(KEY_ESCALATED, false);
        input.put(KEY_GUIDANCE, "");
        input.put(KEY_REPORT, "");
        input.put(KEY_USAGE, TokenUsage.unknown());
        input.put(KEY_MODEL, config.model());

        OverAllState state;
        try {
            state = graph.invoke(input)
                    .orElseThrow(() -> new IllegalStateException("patient risk workflow produced no state"));
        } catch (RuntimeException ex) {
            throw unwrap(ex);
        }

        PatientProfile patient = state.value(KEY_PATIENT, PatientProfile.class).orElse(null);
        HealthMetrics metrics = state.value(KEY_METRICS, HealthMetrics.class).orElse(null);
        RiskLevel riskLevel = state.value(KEY_RISK_LEVEL, RiskLevel.LOW);
        String justification = state.value(KEY_JUSTIFICATION, "");
        boolean escalated = state.value(KEY_ESCALATED, false);
        String report = state.value(KEY_REPORT, "");
        TokenUsage usage = state.value(KEY_USAGE, TokenUsage.unknown());
        String model = state.value(KEY_MODEL, config.model());

        return new PatientRiskWorkflowResult(
                workflowId, patientId, patient, metrics, riskLevel, justification,
                escalated, report, usage, model);
    }

    private RuntimeException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ToolExecutionException toolEx) {
                return toolEx;
            }
            if (current instanceof ChatModelException chatEx) {
                return chatEx;
            }
            current = current.getCause();
        }
        return throwable instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(throwable.getMessage());
    }

    private CompiledGraph buildGraph() {
        StateGraph stateGraph = new StateGraph(this::initialState);
        try {
            stateGraph.addNode(NODE_QUERY_PATIENT, node_async(this::runQueryPatient));
            stateGraph.addNode(NODE_QUERY_METRICS, node_async(this::runQueryMetrics));
            stateGraph.addNode(NODE_JUDGE_RISK, node_async(this::runJudgeRisk));
            stateGraph.addNode(NODE_ESCALATE, node_async(this::runEscalate));
            stateGraph.addNode(NODE_REPORT, node_async(this::runReport));
            stateGraph.addEdge(StateGraph.START, NODE_QUERY_PATIENT);
            stateGraph.addEdge(NODE_QUERY_PATIENT, NODE_QUERY_METRICS);
            stateGraph.addEdge(NODE_QUERY_METRICS, NODE_JUDGE_RISK);
            stateGraph.addConditionalEdges(NODE_JUDGE_RISK,
                    edge_async(state -> state.value(KEY_ROUTE, ROUTE_ROUTINE)),
                    Map.of(ROUTE_URGENT, NODE_ESCALATE, ROUTE_ROUTINE, NODE_REPORT));
            stateGraph.addEdge(NODE_ESCALATE, NODE_REPORT);
            stateGraph.addEdge(NODE_REPORT, StateGraph.END);
            return stateGraph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("failed to build patient risk workflow graph", ex);
        }
    }

    private OverAllState initialState() {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(KEY_PATIENT_ID, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_PATIENT, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_METRICS, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_RISK_LEVEL, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_JUSTIFICATION, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_ROUTE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_ESCALATED, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_GUIDANCE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_REPORT, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_USAGE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_MODEL, new ReplaceStrategy());
        return state;
    }

    private Map<String, Object> runQueryPatient(OverAllState state) {
        String patientId = state.value(KEY_PATIENT_ID, "");
        ToolResult result = toolPort.execute(
                new ToolCall("query_patient", TOOL_PATIENT, toolResultMapper.arguments(patientId)));
        return Map.of(KEY_PATIENT, toolResultMapper.parsePatient(result.output()));
    }

    private Map<String, Object> runQueryMetrics(OverAllState state) {
        String patientId = state.value(KEY_PATIENT_ID, "");
        ToolResult result = toolPort.execute(
                new ToolCall("query_metrics", TOOL_METRICS, toolResultMapper.arguments(patientId)));
        return Map.of(KEY_METRICS, toolResultMapper.parseMetrics(result.output()));
    }

    private Map<String, Object> runJudgeRisk(OverAllState state) {
        HealthMetrics metrics = state.value(KEY_METRICS, HealthMetrics.class).orElse(null);
        RiskAssessment assessment = riskAssessor.assess(metrics);
        boolean escalated = assessment.riskLevel() == RiskLevel.HIGH;
        return Map.of(
                KEY_RISK_LEVEL, assessment.riskLevel(),
                KEY_JUSTIFICATION, assessment.justification(),
                KEY_ROUTE, escalated ? ROUTE_URGENT : ROUTE_ROUTINE,
                KEY_ESCALATED, escalated);
    }

    private Map<String, Object> runEscalate(OverAllState state) {
        return Map.of(KEY_ESCALATED, true, KEY_GUIDANCE, ESCALATION_GUIDANCE);
    }

    private Map<String, Object> runReport(OverAllState state) {
        PromptTemplate system = promptTemplatePort.load("patient-risk-report");
        PatientProfile patient = state.value(KEY_PATIENT, PatientProfile.class).orElse(null);
        HealthMetrics metrics = state.value(KEY_METRICS, HealthMetrics.class).orElse(null);
        RiskLevel riskLevel = state.value(KEY_RISK_LEVEL, RiskLevel.LOW);
        String justification = state.value(KEY_JUSTIFICATION, "");
        boolean escalated = state.value(KEY_ESCALATED, false);
        String guidance = state.value(KEY_GUIDANCE, "");

        List<ChatMessage> messages = List.of(
                new ChatMessage(MessageRole.SYSTEM, system.content()),
                new ChatMessage(MessageRole.USER,
                        buildUserMessage(patient, metrics, riskLevel, justification, escalated, guidance)));
        ChatOptions options = new ChatOptions(config.model(), config.temperature(), config.maxTokens());
        ChatResult result = chatModelPort.chat(messages, options);

        TokenUsage usage = state.value(KEY_USAGE, TokenUsage.unknown()).plus(result.usage());
        String model = result.model() == null || result.model().isBlank() ? config.model() : result.model();
        String report = result.content() == null ? "" : result.content().trim();

        return Map.of(KEY_REPORT, report, KEY_USAGE, usage, KEY_MODEL, model);
    }

    private String buildUserMessage(PatientProfile patient, HealthMetrics metrics, RiskLevel riskLevel,
            String justification, boolean escalated, String guidance) {
        StringBuilder sb = new StringBuilder();
        sb.append("患者信息：").append(patient.name()).append("（").append(patient.patientId())
                .append("，").append(patient.age()).append(" 岁，").append(patient.gender())
                .append("，").append(patient.diagnosis()).append("）\n");
        sb.append("健康指标：收缩压 ").append(metrics.systolic()).append(" mmHg，舒张压 ")
                .append(metrics.diastolic()).append(" mmHg，空腹血糖 ").append(metrics.fastingGlucose())
                .append(" mmol/L，糖化血红蛋白 ").append(metrics.hba1c()).append("%\n");
        sb.append("风险等级：").append(riskLevel.label()).append("\n");
        sb.append("判断依据：").append(justification).append("\n");
        if (escalated) {
            sb.append("加急指引：").append(guidance).append("\n");
        }
        sb.append("请基于以上数据生成一份简洁、可操作的患者风险分析报告。");
        return sb.toString();
    }
}
