package com.aicode.framework.platform.domain.service;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.analysis.application.PatientAnalysisUseCase;
import com.aicode.framework.analysis.domain.model.PatientAnalysisResult;
import com.aicode.framework.analysis.domain.model.PatientAnalysisStep;
import com.aicode.framework.analysis.domain.model.ReportSummary;
import com.aicode.framework.application.FrameworkAgentUseCase;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import com.aicode.framework.followup.application.FollowUpRunResult;
import com.aicode.framework.followup.application.FollowUpTaskUseCase;
import com.aicode.framework.followup.domain.model.FollowUpPlanItem;
import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.followup.domain.model.FollowUpStep;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.multiagent.application.MedicalAssistantUseCase;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.operations.application.OperationsAnalysisUseCase;
import com.aicode.framework.operations.domain.model.AgentRunSummary;
import com.aicode.framework.operations.domain.model.OperationsAnomaly;
import com.aicode.framework.operations.domain.model.OperationsDailyReport;
import com.aicode.framework.operations.domain.model.OperationsSummary;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.port.PlatformRunScopePort;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将平台 Agent 定义调度到各周既有运行时。
 *
 * <p>Week 21 增加 {@link AgentType#PATIENT_ANALYSIS} 分支：从
 * {@link PlatformRunScopePort} 取当前 Run 的租户+用户范围（拿不到就不写记忆），
 * 保证「记忆隔离」不依赖调用方传参。</p>
 */
@Service
public class PlatformAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAgentRunner.class);

    private final FrameworkAgentUseCase frameworkAgentUseCase;
    private final PatientRiskUseCase patientRiskUseCase;
    private final MedicalAssistantUseCase medicalAssistantUseCase;
    private final PatientAnalysisUseCase patientAnalysisUseCase;
    private final PlatformRunScopePort runScopePort;
    private final OperationsAnalysisUseCase operationsAnalysisUseCase;
    private final FollowUpTaskUseCase followUpTaskUseCase;

    /**
     * 兼容 Week 8–20 的三参构造器（不含患者分析能力，供既有单测使用）。
     *
     * @param frameworkAgentUseCase   Week 8 ReAct
     * @param patientRiskUseCase      Week 9-10 Workflow
     * @param medicalAssistantUseCase Week 11 多 Agent
     */
    public PlatformAgentRunner(
            FrameworkAgentUseCase frameworkAgentUseCase,
            PatientRiskUseCase patientRiskUseCase,
            MedicalAssistantUseCase medicalAssistantUseCase
    ) {
        this(frameworkAgentUseCase, patientRiskUseCase, medicalAssistantUseCase,
                null, PlatformRunScopePort.NONE, null, null);
    }

    /**
     * 兼容 Week 21–23 的五参构造器（不含运营分析/随访，供既有单测使用）。
     *
     * @param frameworkAgentUseCase   Week 8 ReAct
     * @param patientRiskUseCase      Week 9-10 Workflow
     * @param medicalAssistantUseCase Week 11 多 Agent
     * @param patientAnalysisUseCase  Week 21 患者分析
     * @param runScopePort            当前 Run 访问范围
     */
    public PlatformAgentRunner(
            FrameworkAgentUseCase frameworkAgentUseCase,
            PatientRiskUseCase patientRiskUseCase,
            MedicalAssistantUseCase medicalAssistantUseCase,
            PatientAnalysisUseCase patientAnalysisUseCase,
            PlatformRunScopePort runScopePort
    ) {
        this(frameworkAgentUseCase, patientRiskUseCase, medicalAssistantUseCase,
                patientAnalysisUseCase, runScopePort, null, null);
    }

    /**
     * 完整构造器（Spring 注入点）。
     *
     * <p>必须显式 {@code @Autowired}：本类有多个构造器，Spring 无法自行选择
     * （第 19/20 周两次在真机暴露过同样的启动失败）。</p>
     *
     * @param frameworkAgentUseCase    Week 8 ReAct
     * @param patientRiskUseCase       Week 9-10 Workflow
     * @param medicalAssistantUseCase  Week 11 多 Agent
     * @param patientAnalysisUseCase   Week 21 患者分析
     * @param runScopePort             当前 Run 访问范围
     * @param operationsAnalysisUseCase Week 24 运营分析
     * @param followUpTaskUseCase      Week 24 随访任务
     */
    @Autowired
    public PlatformAgentRunner(
            FrameworkAgentUseCase frameworkAgentUseCase,
            PatientRiskUseCase patientRiskUseCase,
            MedicalAssistantUseCase medicalAssistantUseCase,
            PatientAnalysisUseCase patientAnalysisUseCase,
            PlatformRunScopePort runScopePort,
            OperationsAnalysisUseCase operationsAnalysisUseCase,
            FollowUpTaskUseCase followUpTaskUseCase
    ) {
        this.frameworkAgentUseCase = frameworkAgentUseCase;
        this.patientRiskUseCase = patientRiskUseCase;
        this.medicalAssistantUseCase = medicalAssistantUseCase;
        this.patientAnalysisUseCase = patientAnalysisUseCase;
        this.runScopePort = runScopePort;
        this.operationsAnalysisUseCase = operationsAnalysisUseCase;
        this.followUpTaskUseCase = followUpTaskUseCase;
    }

    /**
     * 按 Agent 类型执行并返回结构化输出。
     *
     * @param agent 注册表中的 Agent 定义
     * @param input Run 入参
     * @return 输出、用量与模型名
     */
    public PlatformRunOutput run(PlatformAgentDefinition agent, Map<String, Object> input) {
        log.info("[platform] dispatch agentKey={} agentType={}", agent.agentKey(), agent.agentType());
        return switch (agent.agentType()) {
            case FRAMEWORK_REACT -> runFrameworkReact(input);
            case PATIENT_RISK_WORKFLOW -> runPatientRisk(input);
            case MEDICAL_ASSISTANT -> runMedicalAssistant(input);
            case PATIENT_ANALYSIS -> runPatientAnalysis(input);
            case OPERATIONS_ANALYSIS -> runOperationsAnalysis(input);
            case FOLLOW_UP -> runFollowUp(input);
        };
    }

    private PlatformRunOutput runFrameworkReact(Map<String, Object> input) {
        String task = requireString(input, "task");
        FrameworkAgentResult result = frameworkAgentUseCase.run(task);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskId", result.taskId());
        output.put("answer", result.answer());
        output.put("steps", result.steps());
        output.put("totalSteps", result.totalSteps());
        return new PlatformRunOutput(output, result.usage(), result.model());
    }

    private PlatformRunOutput runPatientRisk(Map<String, Object> input) {
        String patientId = requireString(input, "patientId");
        PatientRiskWorkflowResult result = patientRiskUseCase.run(patientId);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("workflowId", result.workflowId());
        output.put("status", result.status().name());
        output.put("patientId", result.patientId());
        output.put("riskLevel", result.riskLevel().name());
        output.put("report", result.report());
        output.put("escalated", result.escalated());
        return new PlatformRunOutput(output, result.usage(), result.model());
    }

    private PlatformRunOutput runMedicalAssistant(Map<String, Object> input) {
        String patientId = requireString(input, "patientId");
        String task = optionalString(input, "task");
        MedicalAssistantResult result = medicalAssistantUseCase.run(patientId, task);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("runId", result.runId());
        output.put("patientId", result.patientId());
        output.put("task", result.task());
        output.put("riskLevel", result.riskLevel().name());
        output.put("report", result.report());
        output.put("followUpPlan", result.followUpPlan());
        output.put("steps", result.steps());
        return new PlatformRunOutput(output, result.usage(), result.model());
    }

    private PlatformRunOutput runPatientAnalysis(Map<String, Object> input) {
        if (patientAnalysisUseCase == null) {
            throw new IllegalStateException("patient analysis use case is not configured");
        }
        String patientId = requireString(input, "patientId");
        String task = optionalString(input, "task");
        PlatformMemoryScope scope = runScopePort.currentScope().orElse(null);
        PatientAnalysisResult result = patientAnalysisUseCase.run(patientId, task, scope);
        Map<String, Object> output = toOutput(result);
        output.put("task", task == null ? PatientAnalysisUseCase.defaultTask(patientId) : task);
        boolean pendingApproval = scope != null && result.requiresApproval();
        return new PlatformRunOutput(output, result.usage(), result.model(), pendingApproval);
    }

    /**
     * Agent 2（Week 24）：运营分析。
     *
     * <p>必须有 Platform Run 范围（才拿得到调用者与租户）；没有范围说明是直连 Run，
     * 直接显式失败，而不是悄悄统计出一个「全库」的数字。</p>
     */
    private PlatformRunOutput runOperationsAnalysis(Map<String, Object> input) {
        if (operationsAnalysisUseCase == null) {
            throw new IllegalStateException("operations analysis use case is not configured");
        }
        PlatformMemoryScope scope = runScopePort.currentScope()
                .orElseThrow(() -> new IllegalStateException("operations analysis requires platform run scope"));
        Integer windowDays = optionalInt(input, "windowDays");
        OperationsDailyReport report = operationsAnalysisUseCase.run(scope.userId(), windowDays);
        Map<String, Object> output = toOutput(report);
        output.put("task", optionalString(input, "task") == null
                ? "生成最近 " + report.windowDays() + " 天运营日报"
                : optionalString(input, "task"));
        return new PlatformRunOutput(output, report.usage(), report.model());
    }

    /**
     * Agent 4（Week 24）：随访计划 + 任务落库。
     *
     * <p>没有 Platform Run 范围时仍返回计划，但**不落库**（与患者分析的记忆写入同一策略），
     * 避免直连 Run 在没有租户/用户的情况下写出无主任务。</p>
     */
    private PlatformRunOutput runFollowUp(Map<String, Object> input) {
        if (followUpTaskUseCase == null) {
            throw new IllegalStateException("follow-up use case is not configured");
        }
        String patientId = requireString(input, "patientId");
        String task = optionalString(input, "task");
        PlatformMemoryScope scope = runScopePort.currentScope().orElse(null);
        FollowUpRunResult result = followUpTaskUseCase.run(patientId, task, scope);
        Map<String, Object> output = toOutput(result);
        output.put("task", task == null ? "为患者 " + patientId + " 生成随访计划" : task);
        return new PlatformRunOutput(output, result.plan().usage(), result.plan().model());
    }

    private Map<String, Object> toOutput(FollowUpRunResult result) {
        FollowUpPlanResult plan = result.plan();
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<String> taskIds = new ArrayList<>();
        for (FollowUpTask task : result.tasks()) {
            taskIds.add(task.taskId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", task.taskId());
            item.put("title", task.title());
            item.put("detail", task.detail());
            item.put("dueAt", task.dueAt().toString());
            item.put("priority", task.priority().name());
            item.put("status", task.status().name());
            tasks.add(item);
        }
        if (tasks.isEmpty()) {
            // 直连 Run（无范围）不落库：仍然把规则计划回显给调用方，便于排障。
            for (FollowUpPlanItem item : plan.tasks()) {
                Map<String, Object> planned = new LinkedHashMap<>();
                planned.put("taskId", null);
                planned.put("title", item.title());
                planned.put("detail", item.detail());
                planned.put("dueAt", item.dueAt().toString());
                planned.put("priority", item.priority().name());
                planned.put("status", "PLANNED");
                tasks.add(planned);
            }
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        for (FollowUpStep step : plan.steps()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepNo", step.stepNo());
            item.put("agent", step.agent());
            item.put("summary", step.summary());
            steps.add(item);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("patientId", plan.patientId());
        output.put("riskLevel", plan.riskLevel());
        output.put("planText", plan.planText());
        output.put("degraded", plan.degraded());
        output.put("taskIds", taskIds);
        output.put("tasks", tasks);
        output.put("steps", steps);
        output.put("totalSteps", steps.size());
        return output;
    }

    private Map<String, Object> toOutput(OperationsDailyReport report) {
        OperationsSummary summary = report.summary();
        Map<String, Object> summaryMap = new LinkedHashMap<>();
        summaryMap.put("totalRuns", summary.totalRuns());
        summaryMap.put("completed", summary.completed());
        summaryMap.put("failed", summary.failed());
        summaryMap.put("pendingApproval", summary.pendingApproval());
        summaryMap.put("rejected", summary.rejected());
        summaryMap.put("running", summary.running());
        summaryMap.put("successRate", summary.successRate());
        summaryMap.put("failureRate", summary.failureRate());
        summaryMap.put("promptTokens", summary.promptTokens());
        summaryMap.put("completionTokens", summary.completionTokens());
        summaryMap.put("totalTokens", summary.totalTokens());
        summaryMap.put("totalCostUsd", summary.totalCostUsd());
        summaryMap.put("costConfigured", summary.costConfigured());
        summaryMap.put("truncated", summary.truncated());

        List<Map<String, Object>> agents = new ArrayList<>();
        for (AgentRunSummary agent : report.agents()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agentKey", agent.agentKey());
            item.put("runs", agent.runs());
            item.put("failures", agent.failures());
            item.put("tokens", agent.tokens());
            agents.add(item);
        }
        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (OperationsAnomaly anomaly : report.anomalies()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", anomaly.code().name());
            item.put("level", anomaly.level().name());
            item.put("message", anomaly.message());
            item.put("value", anomaly.value());
            item.put("threshold", anomaly.threshold());
            anomalies.add(item);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("reportType", "DAILY");
        output.put("windowStart", report.windowStart().toString());
        output.put("windowEnd", report.windowEnd().toString());
        output.put("windowDays", report.windowDays());
        output.put("summary", summaryMap);
        output.put("agents", agents);
        output.put("anomalies", anomalies);
        output.put("report", report.report());
        output.put("degraded", report.degraded());
        return output;
    }

    private Map<String, Object> toOutput(PatientAnalysisResult result) {        Map<String, Object> patient = new LinkedHashMap<>();
        if (result.patient() != null) {
            patient.put("patientId", result.patient().patientId());
            patient.put("name", result.patient().name());
            patient.put("age", result.patient().age());
            patient.put("gender", result.patient().gender());
            patient.put("diagnosis", result.patient().diagnosis());
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (result.metrics() != null) {
            metrics.put("systolic", result.metrics().systolic());
            metrics.put("diastolic", result.metrics().diastolic());
            metrics.put("fastingGlucose", result.metrics().fastingGlucose());
            metrics.put("hba1c", result.metrics().hba1c());
        }
        List<Map<String, Object>> reports = new ArrayList<>();
        for (ReportSummary report : result.reports()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reportId", report.reportId());
            item.put("type", report.type());
            item.put("date", report.date());
            item.put("conclusion", report.conclusion());
            item.put("abnormal", report.abnormal());
            reports.add(item);
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        for (PatientAnalysisStep step : result.steps()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepNo", step.stepNo());
            item.put("agent", step.agent());
            item.put("summary", step.summary());
            steps.add(item);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("patientId", result.patientId());
        output.put("patient", patient);
        output.put("metrics", metrics);
        output.put("reportCount", reports.size());
        output.put("reports", reports);
        output.put("riskLevel", result.riskLevel().name());
        output.put("justification", result.justification());
        output.put("report", result.report());
        output.put("degraded", result.degraded());
        output.put("memoryId", result.memoryId());
        output.put("steps", steps);
        output.put("totalSteps", steps.size());
        return output;
    }

    private String requireString(Map<String, Object> input, String key) {
        Object value = input == null ? null : input.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new InvalidChatRequestException(key + " must not be blank");
        }
        return value.toString().trim();
    }

    private String optionalString(Map<String, Object> input, String key) {
        if (input == null) {
            return null;
        }
        Object value = input.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 可选整数入参：接受 Number 或数字字符串；非法值交给用例层按「越界」处理前先 400。
     *
     * @param input 入参
     * @param key   键
     * @return 数值；缺省或非法返回 null
     */
    private Integer optionalInt(Map<String, Object> input, String key) {
        Object value = input == null ? null : input.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new InvalidChatRequestException(key + " must be an integer");
        }
    }
}
