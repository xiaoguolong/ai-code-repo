package com.aicode.framework.followup.infrastructure;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
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
import com.aicode.framework.followup.domain.FollowUpPlanner;
import com.aicode.framework.followup.domain.model.FollowUpPlan;
import com.aicode.framework.followup.domain.model.FollowUpPlanItem;
import com.aicode.framework.followup.domain.model.FollowUpPlanResult;
import com.aicode.framework.followup.domain.model.FollowUpStep;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskToolResultMapper;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 随访 Agent（Week 24，README 第 6 阶段 Agent 4）。
 *
 * <p>链路：查患者 → 查健康指标 → 规则风险 → {@link FollowUpPlanner} 生成任务条目 →
 * 模型写「计划说明」文本（`follow-up-plan` Prompt）。</p>
 *
 * <p>关键约束：<b>任务条目（时间/优先级/标题）由规则决定</b>，模型只写说明文本；
 * 模型不可用时降级为规则模板并置 {@code degraded=true}。
 * 真正的落库在 {@code FollowUpTaskUseCase}（这里拿不到租户/用户，就不会误写）。</p>
 */
public class FollowUpAgent {

    private static final Logger log = LoggerFactory.getLogger(FollowUpAgent.class);

    private static final String TOOL_PATIENT = "PatientLookupTool";
    private static final String TOOL_METRICS = "HealthMetricTool";
    private static final String PROMPT_NAME = "follow-up-plan";

    static final String STEP_PATIENT = "patient_lookup";
    static final String STEP_METRICS = "metric_lookup";
    static final String STEP_RISK = "risk_analysis";
    static final String STEP_PLAN = "plan_generation";

    private final ToolPort toolPort;
    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final PatientRiskAssessor riskAssessor;
    private final PatientRiskToolResultMapper toolResultMapper;
    private final FollowUpPlanner planner;
    private final FollowUpRuntimeConfig config;
    private final Clock clock;

    public FollowUpAgent(
            ToolPort toolPort,
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            PatientRiskAssessor riskAssessor,
            PatientRiskToolResultMapper toolResultMapper,
            FollowUpPlanner planner,
            FollowUpRuntimeConfig config,
            Clock clock
    ) {
        this.toolPort = toolPort;
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.riskAssessor = riskAssessor;
        this.toolResultMapper = toolResultMapper;
        this.planner = planner == null ? new FollowUpPlanner() : planner;
        this.config = config;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 生成随访计划（不落库）。
     *
     * @param patientId 患者编号（调用方已校验非空）
     * @param task      任务原文，可为空
     * @return 计划（任务条目 + 说明文本 + 轨迹）
     */
    public FollowUpPlanResult run(String patientId, String task) {
        List<FollowUpStep> steps = new ArrayList<>();

        PatientProfile patient = toolResultMapper.parsePatient(
                execute(new ToolCall("query_patient", TOOL_PATIENT, toolResultMapper.arguments(patientId))));
        addStep(steps, STEP_PATIENT, "loaded patient " + patient.patientId());

        HealthMetrics metrics = toolResultMapper.parseMetrics(
                execute(new ToolCall("query_metrics", TOOL_METRICS, toolResultMapper.arguments(patientId))));
        addStep(steps, STEP_METRICS, "loaded metrics");

        RiskAssessment risk = riskAssessor.assess(metrics);
        addStep(steps, STEP_RISK, "risk=" + risk.riskLevel().name());

        FollowUpPlan plan = planner.plan(risk.riskLevel().name(), Instant.now(clock));
        PlanOutcome outcome = generatePlanText(patientId, task, patient, risk, plan);
        addStep(steps, STEP_PLAN, "tasks=" + plan.size() + " degraded=" + outcome.degraded());

        log.info("[followup] run completed patientId={} riskLevel={} tasks={} degraded={} tokens={}",
                patientId, plan.riskLevel(), plan.size(), outcome.degraded(), outcome.usage().totalTokens());

        return new FollowUpPlanResult(
                patientId, plan.riskLevel(), outcome.text(), plan.items(),
                outcome.degraded(), outcome.usage(), outcome.model(), steps);
    }

    private String execute(ToolCall call) {
        return toolPort.execute(call).output();
    }

    private PlanOutcome generatePlanText(
            String patientId,
            String task,
            PatientProfile patient,
            RiskAssessment risk,
            FollowUpPlan plan
    ) {
        try {
            PromptTemplate system = promptTemplatePort.load(PROMPT_NAME);
            ChatResult result = chatModelPort.chat(
                    List.of(
                            new ChatMessage(MessageRole.SYSTEM, system.content()),
                            new ChatMessage(MessageRole.USER, buildUserMessage(task, patient, risk, plan))),
                    new ChatOptions(config.model(), config.temperature(), config.maxTokens()));
            String content = result.content() == null ? "" : result.content().trim();
            if (content.isEmpty()) {
                log.warn("[followup] empty model content, degrade to rule template patientId={}", patientId);
                return new PlanOutcome(composeTemplate(patient, plan), true, TokenUsage.unknown(), config.model());
            }
            String model = (result.model() == null || result.model().isBlank()) ? config.model() : result.model();
            return new PlanOutcome(content, false, result.usage(), model);
        } catch (ChatModelException | InvalidChatRequestException ex) {
            log.warn("[followup] model/prompt unavailable, degrade to rule template patientId={} error={}",
                    patientId, ex.getMessage());
            return new PlanOutcome(composeTemplate(patient, plan), true, TokenUsage.unknown(), config.model());
        }
    }

    private String buildUserMessage(
            String task, PatientProfile patient, RiskAssessment risk, FollowUpPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务：").append(task == null || task.isBlank() ? "生成随访计划" : task).append('\n');
        builder.append("患者：").append(patient.name()).append("（").append(patient.patientId()).append("，")
                .append(patient.age()).append(" 岁，").append(patient.diagnosis()).append("）\n");
        builder.append("规则风险：").append(risk.riskLevel().label())
                .append("（依据：").append(risk.justification()).append("）\n");
        builder.append("已确定的随访任务（时间与优先级不可改动）：\n");
        for (FollowUpPlanItem item : plan.items()) {
            builder.append("- ").append(item.dueAt()).append(' ').append(item.title())
                    .append("（").append(item.priority().name()).append("）：").append(item.detail()).append('\n');
        }
        builder.append("请用中文写一段给医生看的随访计划说明，说明为什么安排这些时间点与观察要点；"
                + "不得新增、删除或改动任务条目，不得编造数据，不得给出处方剂量，全文 300 字以内。");
        return builder.toString();
    }

    private String composeTemplate(PatientProfile patient, FollowUpPlan plan) {
        StringBuilder builder = new StringBuilder("## 随访计划\n");
        builder.append(String.format(Locale.ROOT, "患者：%s（%s），风险等级：%s%n",
                patient.name(), patient.patientId(), plan.riskLevel()));
        int index = 1;
        for (FollowUpPlanItem item : plan.items()) {
            builder.append(String.format(Locale.ROOT, "%d. %s %s —— %s（优先级 %s）%n",
                    index++, item.dueAt(), item.title(), item.detail(), item.priority().name()));
        }
        builder.append("\n> 本计划由规则模板生成（模型不可用或未返回内容）；时间点需医生确认。");
        return builder.toString();
    }

    private void addStep(List<FollowUpStep> steps, String agent, String summary) {
        steps.add(new FollowUpStep(steps.size() + 1, agent, summary));
    }

    /**
     * 计划说明生成结果（内部值对象）。
     *
     * @param text     说明文本
     * @param degraded 是否降级
     * @param usage    token 用量
     * @param model    模型名
     */
    private record PlanOutcome(String text, boolean degraded, TokenUsage usage, String model) {
    }
}
