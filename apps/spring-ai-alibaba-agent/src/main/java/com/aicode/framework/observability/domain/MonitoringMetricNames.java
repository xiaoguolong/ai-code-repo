package com.aicode.framework.observability.domain;

import java.util.List;

/**
 * 指标名常量（Week 20）。
 *
 * <p>这些是 <b>Micrometer 名</b>，不是 Prometheus 导出名。Grafana 面板与告警里用的是导出名：</p>
 *
 * <table border="1">
 *   <caption>名字映射</caption>
 *   <tr><th>常量</th><th>Micrometer</th><th>Prometheus 导出名</th></tr>
 *   <tr><td>{@link #AGENT_RUN_COUNT}</td><td>agent.run.count</td><td>agent_run_count_total</td></tr>
 *   <tr><td>{@link #AGENT_RUN_DURATION}</td><td>agent.run.duration</td><td>agent_run_duration_seconds_*</td></tr>
 *   <tr><td>{@link #LLM_CALL_COUNT}</td><td>llm.call.count</td><td>llm_call_count_total</td></tr>
 *   <tr><td>{@link #LLM_CALL_DURATION}</td><td>llm.call.duration</td><td>llm_call_duration_seconds_*</td></tr>
 *   <tr><td>{@link #LLM_TOKENS}</td><td>llm.tokens.total</td><td>llm_tokens_total</td></tr>
 *   <tr><td>{@link #LLM_COST_USD}</td><td>llm.cost.usd</td><td>llm_cost_usd_total</td></tr>
 *   <tr><td>{@link #TOOL_CALL_COUNT}</td><td>tool.call.count</td><td>tool_call_count_total</td></tr>
 *   <tr><td>{@link #APPROVAL_PENDING_COUNT}</td><td>approval.pending.count</td><td>approval_pending_count</td></tr>
 *   <tr><td>{@link #APPROVAL_REMINDER_COUNT}</td><td>approval.reminder.count</td><td>approval_reminder_count_total</td></tr>
 *   <tr><td>{@link #KNOWLEDGE_SEARCH_DURATION}</td><td>knowledge.search.duration</td><td>knowledge_search_duration_seconds_*</td></tr>
 *   <tr><td>{@link #KNOWLEDGE_HIT_COUNT}</td><td>knowledge.hit.count</td><td>knowledge_hit_count_total</td></tr>
 *   <tr><td>{@link #KNOWLEDGE_RECONCILE_COUNT}</td><td>knowledge.reconcile.count</td><td>knowledge_reconcile_count_total</td></tr>
 *   <tr><td>{@link #KNOWLEDGE_BINDING_MISSING_COUNT}</td><td>knowledge.binding.missing.count</td><td>knowledge_binding_missing_count</td></tr>
 * </table>
 *
 * <p>映射关系由 {@code MonitoringMetricsContractTest} 用真实 Prometheus 注册表抓取文本格式锁死；
 * 改常量或改标签键会让仪表盘静默变空，所以必须先改契约测试。</p>
 */
public final class MonitoringMetricNames {

    /** Agent 执行次数（标签：{@code agent.key} / {@code execution.status}）。 */
    public static final String AGENT_RUN_COUNT = "agent.run.count";

    /** Agent 执行耗时（标签同上）。 */
    public static final String AGENT_RUN_DURATION = "agent.run.duration";

    /** LLM 调用次数（标签：{@code gen_ai.request.model} / {@code llm.outcome}）。 */
    public static final String LLM_CALL_COUNT = "llm.call.count";

    /** LLM 调用耗时（标签同上）。 */
    public static final String LLM_CALL_DURATION = "llm.call.duration";

    /** LLM token 数（标签：{@code gen_ai.request.model} / {@code token.type}）。 */
    public static final String LLM_TOKENS = "llm.tokens.total";

    /** LLM 估算成本（USD；标签：{@code gen_ai.request.model}）。 */
    public static final String LLM_COST_USD = "llm.cost.usd";

    /** 工具调用次数（标签：{@code tool.name} / {@code tool.outcome}）。 */
    public static final String TOOL_CALL_COUNT = "tool.call.count";

    /** 待审审批积压数（Gauge，Week 24；无标签，按全局聚合）。 */
    public static final String APPROVAL_PENDING_COUNT = "approval.pending.count";

    /** 审批超时提醒下发次数（Counter，Week 27；无标签）。 */
    public static final String APPROVAL_REMINDER_COUNT = "approval.reminder.count";

    /** 平台侧知识库检索耗时（Timer，Week 27；标签：{@code knowledge.outcome}）。 */
    public static final String KNOWLEDGE_SEARCH_DURATION = "knowledge.search.duration";

    /** 知识库检索累计命中切片数（Counter，Week 27；无标签，amount = 本次命中数）。 */
    public static final String KNOWLEDGE_HIT_COUNT = "knowledge.hit.count";

    /** 知识库绑定对账结果计数（Counter，Week 27；标签：{@code knowledge.result}）。 */
    public static final String KNOWLEDGE_RECONCILE_COUNT = "knowledge.reconcile.count";

    /** 企业侧已删除的绑定数（Gauge，Week 27；无标签）。 */
    public static final String KNOWLEDGE_BINDING_MISSING_COUNT = "knowledge.binding.missing.count";

    /** 全部指标名（契约测试遍历用）。 */
    private static final List<String> ALL = List.of(
            AGENT_RUN_COUNT, AGENT_RUN_DURATION, LLM_CALL_COUNT, LLM_CALL_DURATION,
            LLM_TOKENS, LLM_COST_USD, TOOL_CALL_COUNT, APPROVAL_PENDING_COUNT,
            APPROVAL_REMINDER_COUNT, KNOWLEDGE_SEARCH_DURATION, KNOWLEDGE_HIT_COUNT,
            KNOWLEDGE_RECONCILE_COUNT, KNOWLEDGE_BINDING_MISSING_COUNT);

    private MonitoringMetricNames() {
    }

    /**
     * 全部指标名。
     *
     * @return 不可变列表
     */
    public static List<String> all() {
        return ALL;
    }
}
