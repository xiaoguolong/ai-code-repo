package com.aicode.framework.operations.infrastructure;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.operations.domain.OperationsAnomalyDetector;
import com.aicode.framework.operations.domain.OperationsReportComposer;
import com.aicode.framework.operations.domain.OperationsStatistics;
import com.aicode.framework.operations.domain.OperationsThresholds;
import com.aicode.framework.operations.domain.model.AgentRunSummary;
import com.aicode.framework.operations.domain.model.OperationsAnalysisRequest;
import com.aicode.framework.operations.domain.model.OperationsAnomaly;
import com.aicode.framework.operations.domain.model.OperationsDailyReport;
import com.aicode.framework.operations.domain.model.OperationsSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * 运营分析 Agent（Week 24，README 第 6 阶段 Agent 2）。
 *
 * <p>链路：窗口记录 → {@link OperationsStatistics}（唯一口径）→ {@link OperationsAnomalyDetector}
 * （6 条规则）→ 模型写日报正文（`operations-daily-report` Prompt）。</p>
 *
 * <p>三条约束：</p>
 * <ol>
 *   <li>模型只写**正文**：所有数字与异常结论来自规则，模型不得增删；</li>
 *   <li>模型不可用/返回空 → 规则模板降级并置 {@code degraded=true}；</li>
 *   <li>送进模型的只有聚合数与异常列表，**不含 patientId / 患者明细**。</li>
 * </ol>
 */
public class OperationsAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(OperationsAnalysisAgent.class);

    static final String PROMPT_NAME = "operations-daily-report";

    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final OperationsStatistics statistics;
    private final OperationsAnomalyDetector anomalyDetector;
    private final OperationsReportComposer reportComposer;
    private final ModelPriceCatalog priceCatalog;
    private final OperationsAnalysisRuntimeConfig config;

    public OperationsAnalysisAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            OperationsStatistics statistics,
            OperationsAnomalyDetector anomalyDetector,
            OperationsReportComposer reportComposer,
            ModelPriceCatalog priceCatalog,
            OperationsAnalysisRuntimeConfig config
    ) {
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.statistics = statistics == null ? new OperationsStatistics() : statistics;
        this.anomalyDetector = anomalyDetector == null ? new OperationsAnomalyDetector() : anomalyDetector;
        this.reportComposer = reportComposer == null ? new OperationsReportComposer() : reportComposer;
        this.priceCatalog = priceCatalog == null ? ModelPriceCatalog.empty() : priceCatalog;
        this.config = config;
    }

    /**
     * 执行一次运营分析。
     *
     * @param request 窗口与记录
     * @param thresholds 异常阈值
     * @return 日报（含汇总、分组、异常与正文）
     */
    public OperationsDailyReport run(OperationsAnalysisRequest request, OperationsThresholds thresholds) {
        OperationsSummary summary = statistics.summarize(
                request.currentRecords(), priceCatalog, request.truncated());
        List<AgentRunSummary> agents = statistics.byAgent(request.currentRecords());
        long previousTokens = statistics.tokens(request.previousRecords());
        List<OperationsAnomaly> anomalies = anomalyDetector.detect(
                summary, agents, previousTokens, request.pendingApprovals(), thresholds);

        ReportOutcome outcome = generateReport(request, summary, agents, anomalies);
        log.info("[operations-analysis] run completed windowStart={} windowEnd={} totalRuns={} failed={} "
                        + "anomalies={} degraded={} tokens={}",
                request.windowStart(), request.windowEnd(), summary.totalRuns(), summary.failed(),
                anomalies.size(), outcome.degraded(), outcome.usage().totalTokens());

        return new OperationsDailyReport(
                request.windowStart(), request.windowEnd(), request.windowDays(),
                summary, agents, anomalies, outcome.report(), outcome.degraded(),
                outcome.usage(), outcome.model());
    }

    private ReportOutcome generateReport(
            OperationsAnalysisRequest request,
            OperationsSummary summary,
            List<AgentRunSummary> agents,
            List<OperationsAnomaly> anomalies
    ) {
        String fallback = reportComposer.compose(
                request.windowStart(), request.windowEnd(), request.windowDays(),
                summary, agents, anomalies);
        try {
            PromptTemplate system = promptTemplatePort.load(PROMPT_NAME);
            ChatResult result = chatModelPort.chat(
                    List.of(
                            new ChatMessage(MessageRole.SYSTEM, system.content()),
                            new ChatMessage(MessageRole.USER, buildUserMessage(request, summary, agents, anomalies))),
                    new ChatOptions(config.model(), config.temperature(), config.maxTokens()));
            String content = result.content() == null ? "" : result.content().trim();
            if (content.isEmpty()) {
                log.warn("[operations-analysis] empty model content, degrade to rule template");
                return new ReportOutcome(fallback, true, TokenUsage.unknown(), config.model());
            }
            String model = (result.model() == null || result.model().isBlank()) ? config.model() : result.model();
            return new ReportOutcome(content, false, result.usage(), model);
        } catch (ChatModelException | InvalidChatRequestException ex) {
            log.warn("[operations-analysis] model/prompt unavailable, degrade to rule template error={}",
                    ex.getMessage());
            return new ReportOutcome(fallback, true, TokenUsage.unknown(), config.model());
        }
    }

    private String buildUserMessage(
            OperationsAnalysisRequest request,
            OperationsSummary summary,
            List<AgentRunSummary> agents,
            List<OperationsAnomaly> anomalies
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("统计窗口：").append(request.windowStart()).append(" → ")
                .append(request.windowEnd()).append("（").append(request.windowDays()).append(" 天）\n");
        builder.append(String.format(Locale.ROOT,
                "执行：总数 %d，成功 %d，失败 %d，待审 %d，拒绝 %d，运行中 %d\n",
                summary.totalRuns(), summary.completed(), summary.failed(),
                summary.pendingApproval(), summary.rejected(), summary.running()));
        builder.append(String.format(Locale.ROOT, "成功率 %.1f%%，失败率 %.1f%%（分母只含成功+失败）\n",
                summary.successRate() * 100, summary.failureRate() * 100));
        builder.append(String.format(Locale.ROOT, "Token：%d（提示 %d / 补全 %d）；成本 %.6f USD（%s）\n",
                summary.totalTokens(), summary.promptTokens(), summary.completionTokens(),
                summary.totalCostUsd(), summary.costConfigured() ? "价目表完整" : "部分模型未配价"));
        builder.append("按 Agent：\n");
        for (AgentRunSummary agent : agents) {
            builder.append(String.format(Locale.ROOT, "- %s：执行 %d、失败 %d、token %d%n",
                    agent.agentKey(), agent.runs(), agent.failures(), agent.tokens()));
        }
        builder.append("规则异常：\n");
        if (anomalies.isEmpty()) {
            builder.append("- 无\n");
        } else {
            for (OperationsAnomaly anomaly : anomalies) {
                builder.append("- [").append(anomaly.level().name()).append("] ")
                        .append(anomaly.code().name()).append("：").append(anomaly.message()).append('\n');
            }
        }
        builder.append("请按「运营概览 / 异常与风险 / 建议动作」三段写日报，只能使用以上数字，禁止编造。");
        return builder.toString();
    }

    /**
     * 日报生成结果（内部值对象）。
     *
     * @param report   正文
     * @param degraded 是否降级
     * @param usage    token 用量
     * @param model    模型名
     */
    private record ReportOutcome(String report, boolean degraded, TokenUsage usage, String model) {
    }
}
