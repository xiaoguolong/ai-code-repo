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
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.workflow.domain.exception.WorkflowNotFoundException;
import com.aicode.framework.workflow.domain.exception.WorkflowNotPendingException;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.domain.model.RiskAssessment;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.aicode.framework.workflow.domain.model.WorkflowStatus;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.constant.SaverConstant;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 患者风险分析 Workflow（领域服务）。固定多节点流程 + 高风险人工审核（HITL）：
 * HIGH 风险在 {@code human_review} 前暂停，经 {@link #resume} 注入人工决策后继续或终止。
 */
public class PatientRiskWorkflow {

    private static final Logger log = LoggerFactory.getLogger(PatientRiskWorkflow.class);

    private static final String NODE_QUERY_PATIENT = "query_patient";
    private static final String NODE_QUERY_METRICS = "query_metrics";
    private static final String NODE_JUDGE_RISK = "judge_risk";
    private static final String NODE_HUMAN_REVIEW = "human_review";
    private static final String NODE_ESCALATE = "escalate";
    private static final String NODE_REPORT = "generate_report";

    private static final String ROUTE_ROUTINE = "routine";
    private static final String ROUTE_URGENT = "urgent";
    private static final String ROUTE_CONTINUE = "continue";
    private static final String ROUTE_REJECT = "reject";

    private static final String TOOL_PATIENT = "PatientLookupTool";
    private static final String TOOL_METRICS = "HealthMetricTool";
    private static final String FEEDBACK_APPROVED = "approved";

    static final String KEY_PATIENT_ID = "patientId";
    static final String KEY_PATIENT = "patient";
    static final String KEY_METRICS = "metrics";
    static final String KEY_RISK_LEVEL = "riskLevel";
    static final String KEY_JUSTIFICATION = "justification";
    static final String KEY_ROUTE = "route";
    static final String KEY_APPROVAL_ROUTE = "approvalRoute";
    static final String KEY_HUMAN_APPROVED = "humanApproved";
    static final String KEY_APPROVED = "approved";
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
    private final MemorySaver checkpointSaver;
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
        this.checkpointSaver = new MemorySaver();
        this.graph = buildGraph(checkpointSaver);
    }

    /** 启动流程（兼容第 9 周 {@code run} 命名）。 */
    public PatientRiskWorkflowResult run(String workflowId, String patientId) {
        return start(workflowId, patientId);
    }

    /** 启动流程；HIGH 风险返回 {@link WorkflowStatus#PENDING_APPROVAL}。 */
    public PatientRiskWorkflowResult start(String workflowId, String patientId) {
        log.info("[workflow] start workflowId={} patientId={}", workflowId, patientId);
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(workflowId).build();
        OverAllState state = invokeGraph(buildInput(patientId), runnableConfig);
        PatientRiskWorkflowResult result = toResult(workflowId, patientId, state, runnableConfig);
        log.info("[workflow] start finished workflowId={} status={} riskLevel={}",
                workflowId, result.status().name(), result.riskLevel().name());
        return result;
    }

    /** 人工审核后恢复；仅 {@link WorkflowStatus#PENDING_APPROVAL} 可调用。 */
    public PatientRiskWorkflowResult resume(String workflowId, boolean approved) {
        log.info("[workflow] resume workflowId={} approved={}", workflowId, approved);
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(workflowId).build();
        requirePendingSnapshot(workflowId, runnableConfig);
        graph.overAllState().updateState(Map.of(KEY_APPROVED, approved));
        OverAllState.HumanFeedback feedback = new OverAllState.HumanFeedback(
                Map.of(FEEDBACK_APPROVED, approved), "");
        OverAllState state = invokeGraphResume(feedback, runnableConfig);
        String patientId = state.value(KEY_PATIENT_ID, "");
        PatientRiskWorkflowResult result = toResult(workflowId, patientId, state, runnableConfig);
        log.info("[workflow] resume finished workflowId={} status={} escalated={}",
                workflowId, result.status().name(), result.escalated());
        return result;
    }

    /** 查询 checkpoint 状态。 */
    public PatientRiskWorkflowResult getRun(String workflowId) {
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(workflowId).build();
        StateSnapshot snapshot = graph.stateOf(runnableConfig)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        String patientId = snapshot.state().value(KEY_PATIENT_ID, "");
        return toResult(workflowId, patientId, snapshot.state(), runnableConfig);
    }

    private void requirePendingSnapshot(String workflowId, RunnableConfig runnableConfig) {
        StateSnapshot snapshot = graph.stateOf(runnableConfig)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        if (!NODE_HUMAN_REVIEW.equals(snapshot.getNext())) {
            throw new WorkflowNotPendingException(workflowId);
        }
    }

    private OverAllState invokeGraph(Map<String, Object> input, RunnableConfig runnableConfig) {
        try {
            graph.invoke(input, runnableConfig)
                    .orElseThrow(() -> new IllegalStateException("patient risk workflow produced no state"));
            return graph.getState(runnableConfig).state();
        } catch (RuntimeException ex) {
            throw unwrap(ex);
        }
    }

    private OverAllState invokeGraphResume(OverAllState.HumanFeedback feedback, RunnableConfig runnableConfig) {
        try {
            graph.resume(feedback, runnableConfig)
                    .orElseThrow(() -> new IllegalStateException("patient risk workflow resume produced no state"));
            return graph.getState(runnableConfig).state();
        } catch (RuntimeException ex) {
            throw unwrap(ex);
        }
    }

    private PatientRiskWorkflowResult toResult(
            String workflowId, String patientId, OverAllState state, RunnableConfig runnableConfig) {
        WorkflowStatus status = resolveStatus(state, runnableConfig);
        PatientProfile patient = state.value(KEY_PATIENT, PatientProfile.class).orElse(null);
        HealthMetrics metrics = state.value(KEY_METRICS, HealthMetrics.class).orElse(null);
        RiskLevel riskLevel = state.value(KEY_RISK_LEVEL, RiskLevel.LOW);
        String justification = state.value(KEY_JUSTIFICATION, "");
        boolean escalated = state.value(KEY_ESCALATED, false);
        String report = state.value(KEY_REPORT, "");
        TokenUsage usage = state.value(KEY_USAGE, TokenUsage.unknown());
        String model = state.value(KEY_MODEL, config.model());
        return new PatientRiskWorkflowResult(
                workflowId, status, patientId, patient, metrics, riskLevel, justification,
                escalated, report, usage, model);
    }

    private WorkflowStatus resolveStatus(OverAllState state, RunnableConfig runnableConfig) {
        if (NODE_HUMAN_REVIEW.equals(graph.getState(runnableConfig).getNext())) {
            return WorkflowStatus.PENDING_APPROVAL;
        }
        Optional<Boolean> humanApproved = state.value(KEY_HUMAN_APPROVED);
        if (humanApproved.isPresent() && !humanApproved.get()) {
            return WorkflowStatus.REJECTED;
        }
        return WorkflowStatus.COMPLETED;
    }

    private Map<String, Object> buildInput(String patientId) {
        Map<String, Object> input = new HashMap<>();
        input.put(KEY_PATIENT_ID, patientId);
        input.put(KEY_ROUTE, ROUTE_ROUTINE);
        input.put(KEY_ESCALATED, false);
        input.put(KEY_GUIDANCE, "");
        input.put(KEY_REPORT, "");
        input.put(KEY_USAGE, TokenUsage.unknown());
        input.put(KEY_MODEL, config.model());
        return input;
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
            if (current instanceof PlatformAccessDeniedException deniedEx) {
                return deniedEx;
            }
            current = current.getCause();
        }
        return throwable instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(throwable.getMessage());
    }

    private CompiledGraph buildGraph(MemorySaver saver) {
        StateGraph stateGraph = new StateGraph(this::initialState);
        try {
            stateGraph.addNode(NODE_QUERY_PATIENT, node_async(this::runQueryPatient));
            stateGraph.addNode(NODE_QUERY_METRICS, node_async(this::runQueryMetrics));
            stateGraph.addNode(NODE_JUDGE_RISK, node_async(this::runJudgeRisk));
            stateGraph.addNode(NODE_HUMAN_REVIEW, node_async(this::runHumanReview));
            stateGraph.addNode(NODE_ESCALATE, node_async(this::runEscalate));
            stateGraph.addNode(NODE_REPORT, node_async(this::runReport));
            stateGraph.addEdge(StateGraph.START, NODE_QUERY_PATIENT);
            stateGraph.addEdge(NODE_QUERY_PATIENT, NODE_QUERY_METRICS);
            stateGraph.addEdge(NODE_QUERY_METRICS, NODE_JUDGE_RISK);
            stateGraph.addConditionalEdges(NODE_JUDGE_RISK,
                    edge_async(state -> state.value(KEY_ROUTE, ROUTE_ROUTINE)),
                    Map.of(ROUTE_URGENT, NODE_HUMAN_REVIEW, ROUTE_ROUTINE, NODE_REPORT));
            stateGraph.addConditionalEdges(NODE_HUMAN_REVIEW,
                    edge_async(state -> state.value(KEY_APPROVAL_ROUTE, ROUTE_REJECT)),
                    Map.of(ROUTE_CONTINUE, NODE_ESCALATE, ROUTE_REJECT, StateGraph.END));
            stateGraph.addEdge(NODE_ESCALATE, NODE_REPORT);
            stateGraph.addEdge(NODE_REPORT, StateGraph.END);

            SaverConfig saverConfig = SaverConfig.builder().register(SaverConstant.MEMORY, saver).build();
            CompileConfig compileConfig = CompileConfig.builder()
                    .saverConfig(saverConfig)
                    .interruptBefore(NODE_HUMAN_REVIEW)
                    .build();
            return stateGraph.compile(compileConfig);
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
        state.registerKeyAndStrategy(KEY_APPROVAL_ROUTE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_HUMAN_APPROVED, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_APPROVED, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_ESCALATED, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_GUIDANCE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_REPORT, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_USAGE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_MODEL, new ReplaceStrategy());
        return state;
    }

    private Map<String, Object> runQueryPatient(OverAllState state) {
        String patientId = state.value(KEY_PATIENT_ID, "");
        log.info("[workflow] node={} patientId={}", NODE_QUERY_PATIENT, patientId);
        ToolResult result = toolPort.execute(
                new ToolCall("query_patient", TOOL_PATIENT, toolResultMapper.arguments(patientId)));
        return Map.of(KEY_PATIENT, toolResultMapper.parsePatient(result.output()));
    }

    private Map<String, Object> runQueryMetrics(OverAllState state) {
        String patientId = state.value(KEY_PATIENT_ID, "");
        log.info("[workflow] node={} patientId={}", NODE_QUERY_METRICS, patientId);
        ToolResult result = toolPort.execute(
                new ToolCall("query_metrics", TOOL_METRICS, toolResultMapper.arguments(patientId)));
        return Map.of(KEY_METRICS, toolResultMapper.parseMetrics(result.output()));
    }

    private Map<String, Object> runJudgeRisk(OverAllState state) {
        HealthMetrics metrics = state.value(KEY_METRICS, HealthMetrics.class).orElse(null);
        RiskAssessment assessment = riskAssessor.assess(metrics);
        boolean highRisk = assessment.riskLevel() == RiskLevel.HIGH;
        log.info("[workflow] node={} patientId={} riskLevel={} route={}",
                NODE_JUDGE_RISK, state.value(KEY_PATIENT_ID, ""), assessment.riskLevel().name(),
                highRisk ? ROUTE_URGENT : ROUTE_ROUTINE);
        return Map.of(
                KEY_RISK_LEVEL, assessment.riskLevel(),
                KEY_JUSTIFICATION, assessment.justification(),
                KEY_ROUTE, highRisk ? ROUTE_URGENT : ROUTE_ROUTINE);
    }

    private Map<String, Object> runHumanReview(OverAllState state) {
        Boolean approved = resolveApproval(state);
        if (approved == null) {
            throw new IllegalStateException("human_review missing approval decision");
        }
        log.info("[workflow] node={} patientId={} approved={}",
                NODE_HUMAN_REVIEW, state.value(KEY_PATIENT_ID, ""), approved);
        if (approved) {
            return Map.of(KEY_HUMAN_APPROVED, true, KEY_APPROVAL_ROUTE, ROUTE_CONTINUE);
        }
        return Map.of(KEY_HUMAN_APPROVED, false, KEY_APPROVAL_ROUTE, ROUTE_REJECT);
    }

    private Boolean resolveApproval(OverAllState state) {
        OverAllState.HumanFeedback feedback = state.humanFeedback();
        if (feedback != null && feedback.data().containsKey(FEEDBACK_APPROVED)) {
            return Boolean.TRUE.equals(feedback.data().get(FEEDBACK_APPROVED));
        }
        return state.value(KEY_APPROVED, (Boolean) null);
    }

    private Map<String, Object> runEscalate(OverAllState state) {
        log.info("[workflow] node={} patientId={}", NODE_ESCALATE, state.value(KEY_PATIENT_ID, ""));
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
        log.info("[workflow] node={} patientId={} reportChars={} model={}",
                NODE_REPORT, state.value(KEY_PATIENT_ID, ""), report.length(), model);
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
