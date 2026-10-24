package com.aicode.framework.analysis.domain;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.analysis.application.PatientAnalysisRuntimeConfig;
import com.aicode.framework.analysis.domain.model.PatientAnalysisResult;
import com.aicode.framework.analysis.domain.model.PatientAnalysisStep;
import com.aicode.framework.analysis.domain.model.ReportSummary;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskAssessment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 患者分析 Agent（Week 21，README 第 6 阶段 Agent 1）。
 *
 * <p>链路刻意做成「确定性步骤 + 一次模型调用」：</p>
 * <ol>
 *   <li>查患者、查检查报告、查健康指标（工具，经 RBAC + Guardrail + 观测装饰器）；</li>
 *   <li>规则风险评估（{@link PatientRiskAssessor}，可解释、可单测、不烧 token）；</li>
 *   <li>模型生成报告；模型不可用时降级为规则模板并置 {@code degraded=true}；</li>
 *   <li>长期记忆由用例层写入（本类不接触租户/用户，拿不到 scope 就不会误写）。</li>
 * </ol>
 */
public class PatientAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(PatientAnalysisAgent.class);

    private static final String TOOL_PATIENT = "PatientLookupTool";
    private static final String TOOL_REPORT = "ReportLookupTool";
    private static final String TOOL_METRICS = "HealthMetricTool";
    private static final String PROMPT_NAME = "patient-analysis";

    static final String STEP_PATIENT = "patient_lookup";
    static final String STEP_REPORT = "report_lookup";
    static final String STEP_METRICS = "metric_lookup";
    static final String STEP_RISK = "risk_analysis";
    static final String STEP_REPORT_GENERATION = "report_generation";

    private final ToolPort toolPort;
    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final PatientRiskAssessor riskAssessor;
    private final PatientRiskToolResultMapper toolResultMapper;
    private final ObjectMapper objectMapper;
    private final PatientAnalysisRuntimeConfig config;
    private final PatientAnalysisReportComposer reportComposer = new PatientAnalysisReportComposer();

    public PatientAnalysisAgent(
            ToolPort toolPort,
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            PatientRiskAssessor riskAssessor,
            PatientRiskToolResultMapper toolResultMapper,
            ObjectMapper objectMapper,
            PatientAnalysisRuntimeConfig config
    ) {
        this.toolPort = toolPort;
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.riskAssessor = riskAssessor;
        this.toolResultMapper = toolResultMapper;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /**
     * 执行一次患者分析。
     *
     * @param patientId 患者编号（调用方已校验非空）
     * @param task      任务描述（用于报告生成，可为空串）
     * @return 结构化结果
     * @throws ToolExecutionException 任一工具失败（不吞错）
     */
    public PatientAnalysisResult run(String patientId, String task) {
        List<PatientAnalysisStep> steps = new ArrayList<>();

        PatientProfile patient = toolResultMapper.parsePatient(
                execute(new ToolCall("query_patient", TOOL_PATIENT, toolResultMapper.arguments(patientId))));
        addStep(steps, STEP_PATIENT, "loaded patient " + patient.patientId());

        List<ReportSummary> reports = parseReports(
                execute(new ToolCall("query_reports", TOOL_REPORT, toolResultMapper.arguments(patientId))));
        addStep(steps, STEP_REPORT, "loaded reports count=" + reports.size());

        HealthMetrics metrics = toolResultMapper.parseMetrics(
                execute(new ToolCall("query_metrics", TOOL_METRICS, toolResultMapper.arguments(patientId))));
        addStep(steps, STEP_METRICS, "loaded metrics");

        RiskAssessment risk = riskAssessor.assess(metrics);
        addStep(steps, STEP_RISK, "risk=" + risk.riskLevel().name());

        ReportOutcome reportOutcome = generateReport(patientId, task, patient, metrics, reports, risk);
        addStep(steps, STEP_REPORT_GENERATION, "generated report degraded=" + reportOutcome.degraded());

        log.info("[patient-analysis] run completed patientId={} reports={} riskLevel={} degraded={} tokens={}",
                patientId, reports.size(), risk.riskLevel().name(), reportOutcome.degraded(),
                reportOutcome.usage().totalTokens());

        return new PatientAnalysisResult(
                patientId, patient, metrics, reports, risk.riskLevel(), risk.justification(),
                reportOutcome.report(), reportOutcome.degraded(), null, steps,
                reportOutcome.usage(), reportOutcome.model());
    }

    private String execute(ToolCall call) {
        return toolPort.execute(call).output();
    }

    private ReportOutcome generateReport(
            String patientId,
            String task,
            PatientProfile patient,
            HealthMetrics metrics,
            List<ReportSummary> reports,
            RiskAssessment risk
    ) {
        try {
            PromptTemplate system = promptTemplatePort.load(PROMPT_NAME);
            ChatResult result = chatModelPort.chat(
                    List.of(
                            new ChatMessage(MessageRole.SYSTEM, system.content()),
                            new ChatMessage(MessageRole.USER, buildUserMessage(patientId, task, patient, metrics, reports, risk))),
                    new ChatOptions(config.model(), config.temperature(), config.maxTokens()));
            String content = result.content() == null ? "" : result.content().trim();
            String model = (result.model() == null || result.model().isBlank()) ? config.model() : result.model();
            if (content.isEmpty()) {
                log.warn("[patient-analysis] empty model content, degrade to rule template patientId={}", patientId);
                return new ReportOutcome(
                        reportComposer.compose(patient, metrics, reports, risk),
                        true, TokenUsage.unknown(), model);
            }
            return new ReportOutcome(content, false, result.usage(), model);
        } catch (ChatModelException ex) {
            log.warn("[patient-analysis] model failed, degrade to rule template patientId={} error={}",
                    patientId, ex.getMessage());
            return new ReportOutcome(
                    reportComposer.compose(patient, metrics, reports, risk),
                    true, TokenUsage.unknown(), config.model());
        }
    }

    private String buildUserMessage(
            String patientId,
            String task,
            PatientProfile patient,
            HealthMetrics metrics,
            List<ReportSummary> reports,
            RiskAssessment risk
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务：").append(task == null || task.isBlank() ? "生成患者分析报告" : task).append('\n');
        builder.append("患者：").append(patient.name()).append("（").append(patientId).append("，")
                .append(patient.age()).append(" 岁，").append(patient.diagnosis()).append("）\n");
        builder.append("指标：收缩压 ").append(metrics.systolic())
                .append("，舒张压 ").append(metrics.diastolic())
                .append("，空腹血糖 ").append(metrics.fastingGlucose())
                .append("，HbA1c ").append(metrics.hba1c()).append('\n');
        builder.append("检查报告：").append(reports.size()).append(" 条\n");
        for (ReportSummary report : reports) {
            builder.append("- ").append(report.date()).append(' ').append(report.type())
                    .append("：").append(report.conclusion())
                    .append(report.abnormal() ? "（异常）" : "").append('\n');
        }
        builder.append("规则风险：").append(risk.riskLevel().label())
                .append("（依据：").append(risk.justification()).append("）\n");
        builder.append("请生成简洁、可操作的患者分析报告，包含风险解读与随访建议；不得编造数据。");
        return builder.toString();
    }

    private List<ReportSummary> parseReports(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("reports");
            if (!items.isArray()) {
                return List.of();
            }
            List<ReportSummary> reports = new ArrayList<>();
            for (JsonNode item : items) {
                reports.add(new ReportSummary(
                        item.path("reportId").asText(),
                        item.path("type").asText(),
                        item.path("date").asText(),
                        item.path("conclusion").asText(),
                        item.path("abnormal").asBoolean(false)));
            }
            return reports;
        } catch (JsonProcessingException ex) {
            throw new ToolExecutionException("failed to parse report tool result", ex);
        }
    }

    private void addStep(List<PatientAnalysisStep> steps, String agent, String summary) {
        steps.add(new PatientAnalysisStep(steps.size() + 1, agent, summary));
    }

    /**
     * 报告生成结果（内部值对象）。
     *
     * @param report   正文
     * @param degraded 是否降级
     * @param usage    token 用量
     * @param model    模型名
     */
    private record ReportOutcome(String report, boolean degraded, TokenUsage usage, String model) {
    }
}
