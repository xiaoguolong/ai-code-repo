package com.aicode.framework.multiagent.domain;

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
import com.aicode.framework.multiagent.application.MedicalAssistantRuntimeConfig;
import com.aicode.framework.multiagent.domain.exception.MedicalAssistantLoopExceededException;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantStep;
import com.aicode.framework.multiagent.domain.model.SupervisorRoute;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskAssessment;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 医疗助手 Supervisor 多 Agent Graph：Supervisor 调度数据 / 分析 / 报告 / 随访四个 Worker。
 */
public class MedicalAssistantSupervisorGraph {

    private static final Logger log = LoggerFactory.getLogger(MedicalAssistantSupervisorGraph.class);

    private static final String NODE_SUPERVISOR = "supervisor";
    private static final String NODE_DATA = "data_agent";
    private static final String NODE_ANALYSIS = "analysis_agent";
    private static final String NODE_REPORT = "report_agent";
    private static final String NODE_FOLLOWUP = "followup_agent";

    private static final String TOOL_PATIENT = "PatientLookupTool";
    private static final String TOOL_METRICS = "HealthMetricTool";

    static final String KEY_RUN_ID = "runId";
    static final String KEY_TASK = "task";
    static final String KEY_PATIENT_ID = "patientId";
    static final String KEY_PATIENT = MedicalAssistantSupervisor.KEY_PATIENT;
    static final String KEY_METRICS = MedicalAssistantSupervisor.KEY_METRICS;
    static final String KEY_RISK_LEVEL = MedicalAssistantSupervisor.KEY_RISK_LEVEL;
    static final String KEY_JUSTIFICATION = "justification";
    static final String KEY_REPORT = MedicalAssistantSupervisor.KEY_REPORT;
    static final String KEY_FOLLOW_UP_PLAN = MedicalAssistantSupervisor.KEY_FOLLOW_UP_PLAN;
    static final String KEY_ROUTE = "route";
    static final String KEY_STEPS = "steps";
    static final String KEY_STEP_NO = "stepNo";
    static final String KEY_SUPERVISOR_LOOPS = "supervisorLoops";
    static final String KEY_USAGE = "usage";
    static final String KEY_MODEL = "model";

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ToolPort toolPort;
    private final PatientRiskAssessor riskAssessor;
    private final PatientRiskToolResultMapper toolResultMapper;
    private final MedicalAssistantSupervisor supervisor;
    private final MedicalAssistantRuntimeConfig config;
    private final CompiledGraph graph;

    public MedicalAssistantSupervisorGraph(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ToolPort toolPort,
            PatientRiskAssessor riskAssessor,
            PatientRiskToolResultMapper toolResultMapper,
            MedicalAssistantSupervisor supervisor,
            MedicalAssistantRuntimeConfig config
    ) {
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.toolPort = toolPort;
        this.riskAssessor = riskAssessor;
        this.toolResultMapper = toolResultMapper;
        this.supervisor = supervisor;
        this.config = config;
        this.graph = buildGraph();
    }

    /**
     * 执行一次 Supervisor 多 Agent 流程。
     */
    public MedicalAssistantResult run(String runId, String patientId, String task) {
        log.info("[multi-agent] run started runId={} patientId={} task={}", runId, patientId, task);
        Map<String, Object> input = buildInput(runId, patientId, task);
        OverAllState state;
        try {
            state = graph.invoke(input)
                    .orElseThrow(() -> new IllegalStateException("medical assistant graph produced no state"));
        } catch (RuntimeException ex) {
            log.warn("[multi-agent] run failed runId={} patientId={} error={}", runId, patientId, ex.getMessage());
            throw unwrap(ex);
        }
        MedicalAssistantResult result = toResult(runId, patientId, task, state);
        log.info("[multi-agent] run completed runId={} patientId={} riskLevel={} steps={} tokens={}",
                runId, patientId, result.riskLevel().name(), result.steps().size(), result.usage().totalTokens());
        return result;
    }

    private Map<String, Object> buildInput(String runId, String patientId, String task) {
        Map<String, Object> input = new HashMap<>();
        input.put(KEY_RUN_ID, runId);
        input.put(KEY_PATIENT_ID, patientId);
        input.put(KEY_TASK, task);
        input.put(KEY_REPORT, "");
        input.put(KEY_FOLLOW_UP_PLAN, "");
        input.put(KEY_JUSTIFICATION, "");
        input.put(KEY_ROUTE, SupervisorRoute.FINISH.graphRoute());
        input.put(KEY_STEPS, new ArrayList<MedicalAssistantStep>());
        input.put(KEY_STEP_NO, 0);
        input.put(KEY_SUPERVISOR_LOOPS, 0);
        input.put(KEY_USAGE, TokenUsage.unknown());
        input.put(KEY_MODEL, config.model());
        return input;
    }

    private MedicalAssistantResult toResult(String runId, String patientId, String task, OverAllState state) {
        PatientProfile patient = state.value(KEY_PATIENT, PatientProfile.class).orElse(null);
        HealthMetrics metrics = state.value(KEY_METRICS, HealthMetrics.class).orElse(null);
        RiskLevel riskLevel = state.value(KEY_RISK_LEVEL, RiskLevel.class).orElse(RiskLevel.LOW);
        String justification = state.value(KEY_JUSTIFICATION, "");
        String report = state.value(KEY_REPORT, "");
        String followUpPlan = state.value(KEY_FOLLOW_UP_PLAN, "");
        List<MedicalAssistantStep> steps = state.value(KEY_STEPS, List.<MedicalAssistantStep>of());
        TokenUsage usage = state.value(KEY_USAGE, TokenUsage.unknown());
        String model = state.value(KEY_MODEL, config.model());
        return new MedicalAssistantResult(
                runId, patientId, task, patient, metrics, riskLevel, justification,
                report, followUpPlan, steps, usage, model);
    }

    private RuntimeException unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MedicalAssistantLoopExceededException loopEx) {
                return loopEx;
            }
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

    private CompiledGraph buildGraph() {
        StateGraph stateGraph = new StateGraph(this::initialState);
        try {
            stateGraph.addNode(NODE_SUPERVISOR, node_async(this::runSupervisor));
            stateGraph.addNode(NODE_DATA, node_async(this::runDataAgent));
            stateGraph.addNode(NODE_ANALYSIS, node_async(this::runAnalysisAgent));
            stateGraph.addNode(NODE_REPORT, node_async(this::runReportAgent));
            stateGraph.addNode(NODE_FOLLOWUP, node_async(this::runFollowupAgent));

            stateGraph.addEdge(StateGraph.START, NODE_SUPERVISOR);
            stateGraph.addConditionalEdges(NODE_SUPERVISOR,
                    edge_async(state -> state.value(KEY_ROUTE, SupervisorRoute.FINISH.graphRoute())),
                    Map.of(
                            SupervisorRoute.DATA.graphRoute(), NODE_DATA,
                            SupervisorRoute.ANALYSIS.graphRoute(), NODE_ANALYSIS,
                            SupervisorRoute.REPORT.graphRoute(), NODE_REPORT,
                            SupervisorRoute.FOLLOWUP.graphRoute(), NODE_FOLLOWUP,
                            SupervisorRoute.FINISH.graphRoute(), StateGraph.END));
            stateGraph.addEdge(NODE_DATA, NODE_SUPERVISOR);
            stateGraph.addEdge(NODE_ANALYSIS, NODE_SUPERVISOR);
            stateGraph.addEdge(NODE_REPORT, NODE_SUPERVISOR);
            stateGraph.addEdge(NODE_FOLLOWUP, NODE_SUPERVISOR);

            CompiledGraph compiled = stateGraph.compile();
            compiled.setMaxIterations(config.maxSupervisorLoops() * 2 + 5);
            return compiled;
        } catch (GraphStateException ex) {
            throw new IllegalStateException("failed to build medical assistant supervisor graph", ex);
        }
    }

    private OverAllState initialState() {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(KEY_RUN_ID, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_TASK, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_PATIENT_ID, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_PATIENT, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_METRICS, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_RISK_LEVEL, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_JUSTIFICATION, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_REPORT, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_FOLLOW_UP_PLAN, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_ROUTE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_STEPS, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_STEP_NO, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_SUPERVISOR_LOOPS, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_USAGE, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY_MODEL, new ReplaceStrategy());
        return state;
    }

    private Map<String, Object> runSupervisor(OverAllState state) {
        int loops = state.value(KEY_SUPERVISOR_LOOPS, 0);
        if (loops >= config.maxSupervisorLoops()) {
            throw new MedicalAssistantLoopExceededException(
                    "medical assistant supervisor exceeded max loops: " + config.maxSupervisorLoops());
        }

        SupervisorRoute route = supervisor.planNext(state);
        StepUpdate stepUpdate = appendStep(state, NODE_SUPERVISOR, "route=" + route.name());
        log.info("[multi-agent] step={} agent={} runId={} patientId={} route={}",
                stepUpdate.stepNo(), NODE_SUPERVISOR, state.value(KEY_RUN_ID, ""), state.value(KEY_PATIENT_ID, ""),
                route.name());
        return Map.of(
                KEY_ROUTE, route.graphRoute(),
                KEY_STEPS, stepUpdate.steps(),
                KEY_STEP_NO, stepUpdate.stepNo(),
                KEY_SUPERVISOR_LOOPS, loops + 1);
    }


    private Map<String, Object> runDataAgent(OverAllState state) {
        String patientId = state.value(KEY_PATIENT_ID, "");
        ToolResult patientResult = toolPort.execute(
                new ToolCall("query_patient", TOOL_PATIENT, toolResultMapper.arguments(patientId)));
        ToolResult metricsResult = toolPort.execute(
                new ToolCall("query_metrics", TOOL_METRICS, toolResultMapper.arguments(patientId)));
        PatientProfile patient = toolResultMapper.parsePatient(patientResult.output());
        HealthMetrics metrics = toolResultMapper.parseMetrics(metricsResult.output());

        StepUpdate stepUpdate = appendStep(state, NODE_DATA, "loaded patient and metrics");
        log.info("[multi-agent] step={} agent={} runId={} patientId={} patient={} metrics=systolic/{}/diastolic/{}",
                stepUpdate.stepNo(), NODE_DATA, state.value(KEY_RUN_ID, ""), patientId,
                patient.name(), metrics.systolic(), metrics.diastolic());
        return Map.of(
                KEY_PATIENT, patient,
                KEY_METRICS, metrics,
                KEY_STEPS, stepUpdate.steps(),
                KEY_STEP_NO, stepUpdate.stepNo());
    }

    private Map<String, Object> runAnalysisAgent(OverAllState state) {
        HealthMetrics metrics = state.value(KEY_METRICS, HealthMetrics.class)
                .orElseThrow(() -> new IllegalStateException("analysis_agent missing metrics"));
        RiskAssessment assessment = riskAssessor.assess(metrics);
        StepUpdate stepUpdate = appendStep(state, NODE_ANALYSIS,
                "risk=" + assessment.riskLevel().name());
        log.info("[multi-agent] step={} agent={} runId={} patientId={} riskLevel={} justification={}",
                stepUpdate.stepNo(), NODE_ANALYSIS, state.value(KEY_RUN_ID, ""), state.value(KEY_PATIENT_ID, ""),
                assessment.riskLevel().name(), assessment.justification());
        return Map.of(
                KEY_RISK_LEVEL, assessment.riskLevel(),
                KEY_JUSTIFICATION, assessment.justification(),
                KEY_STEPS, stepUpdate.steps(),
                KEY_STEP_NO, stepUpdate.stepNo());
    }

    private Map<String, Object> runReportAgent(OverAllState state) {
        PromptTemplate system = promptTemplatePort.load("medical-report");
        ChatResult result = chatModelPort.chat(
                List.of(
                        new ChatMessage(MessageRole.SYSTEM, system.content()),
                        new ChatMessage(MessageRole.USER, buildReportUserMessage(state))),
                new ChatOptions(config.model(), config.temperature(), config.maxTokens()));

        TokenUsage usage = state.value(KEY_USAGE, TokenUsage.unknown()).plus(result.usage());
        String model = result.model() == null || result.model().isBlank() ? config.model() : result.model();
        String report = result.content() == null ? "" : result.content().trim();

        StepUpdate stepUpdate = appendStep(state, NODE_REPORT, "generated report");
        log.info("[multi-agent] step={} agent={} runId={} patientId={} reportChars={} model={}",
                stepUpdate.stepNo(), NODE_REPORT, state.value(KEY_RUN_ID, ""), state.value(KEY_PATIENT_ID, ""),
                report.length(), model);
        return Map.of(
                KEY_REPORT, report,
                KEY_USAGE, usage,
                KEY_MODEL, model,
                KEY_STEPS, stepUpdate.steps(),
                KEY_STEP_NO, stepUpdate.stepNo());
    }

    private Map<String, Object> runFollowupAgent(OverAllState state) {
        PromptTemplate system = promptTemplatePort.load("medical-followup");
        ChatResult result = chatModelPort.chat(
                List.of(
                        new ChatMessage(MessageRole.SYSTEM, system.content()),
                        new ChatMessage(MessageRole.USER, buildFollowupUserMessage(state))),
                new ChatOptions(config.model(), config.temperature(), config.maxTokens()));

        TokenUsage usage = state.value(KEY_USAGE, TokenUsage.unknown()).plus(result.usage());
        String model = result.model() == null || result.model().isBlank() ? config.model() : result.model();
        String followUpPlan = result.content() == null ? "" : result.content().trim();

        StepUpdate stepUpdate = appendStep(state, NODE_FOLLOWUP, "generated follow-up plan");
        log.info("[multi-agent] step={} agent={} runId={} patientId={} followUpChars={} model={}",
                stepUpdate.stepNo(), NODE_FOLLOWUP, state.value(KEY_RUN_ID, ""), state.value(KEY_PATIENT_ID, ""),
                followUpPlan.length(), model);
        return Map.of(
                KEY_FOLLOW_UP_PLAN, followUpPlan,
                KEY_USAGE, usage,
                KEY_MODEL, model,
                KEY_STEPS, stepUpdate.steps(),
                KEY_STEP_NO, stepUpdate.stepNo());
    }

    private String buildReportUserMessage(OverAllState state) {
        PatientProfile patient = state.value(KEY_PATIENT, PatientProfile.class).orElse(null);
        HealthMetrics metrics = state.value(KEY_METRICS, HealthMetrics.class).orElse(null);
        RiskLevel riskLevel = state.value(KEY_RISK_LEVEL, RiskLevel.class).orElse(RiskLevel.LOW);
        String justification = state.value(KEY_JUSTIFICATION, "");
        String task = state.value(KEY_TASK, "");

        StringBuilder sb = new StringBuilder();
        sb.append("任务：").append(task).append("\n");
        if (patient != null) {
            sb.append("患者：").append(patient.name()).append("（").append(patient.patientId())
                    .append("，").append(patient.age()).append(" 岁，").append(patient.diagnosis()).append("）\n");
        }
        if (metrics != null) {
            sb.append("指标：收缩压 ").append(metrics.systolic()).append("，舒张压 ")
                    .append(metrics.diastolic()).append("，空腹血糖 ")
                    .append(metrics.fastingGlucose()).append("，HbA1c ").append(metrics.hba1c()).append("\n");
        }
        sb.append("风险：").append(riskLevel.label()).append("\n");
        sb.append("依据：").append(justification).append("\n");
        sb.append("请生成简洁、可操作的患者分析报告。");
        return sb.toString();
    }

    private String buildFollowupUserMessage(OverAllState state) {
        PatientProfile patient = state.value(KEY_PATIENT, PatientProfile.class).orElse(null);
        RiskLevel riskLevel = state.value(KEY_RISK_LEVEL, RiskLevel.class).orElse(RiskLevel.LOW);
        String report = state.value(KEY_REPORT, "");
        String task = state.value(KEY_TASK, "");

        StringBuilder sb = new StringBuilder();
        sb.append("任务：").append(task).append("\n");
        if (patient != null) {
            sb.append("患者：").append(patient.name()).append("（").append(patient.patientId()).append("）\n");
        }
        sb.append("风险等级：").append(riskLevel.label()).append("\n");
        sb.append("分析报告：\n").append(report).append("\n");
        sb.append("请基于以上信息生成可执行的随访计划（监测频率、生活方式、复诊建议）。");
        return sb.toString();
    }

    private StepUpdate appendStep(OverAllState state, String agentName, String summary) {
        int stepNo = state.value(KEY_STEP_NO, 0) + 1;
        List<MedicalAssistantStep> steps = new ArrayList<>(state.value(KEY_STEPS, List.<MedicalAssistantStep>of()));
        steps.add(new MedicalAssistantStep(stepNo, agentName, summary));
        return new StepUpdate(steps, stepNo);
    }

    private record StepUpdate(List<MedicalAssistantStep> steps, int stepNo) {
    }
}
