package com.aicode.gateway.domain.model;

/**
 * 网关自有指标名与标签常量（Week 20）。
 *
 * <p>第 19 周网关只暴露 {@code health,info}：HTTP/Feign/JVM 指标只进了 SkyWalking，
 * Prometheus 抓不到网关。本周开指标出口后，网关用例需要补四个「只有网关知道」的指标，
 * 名字与标签集中在这里，避免用例里散落魔法字符串（规范 5.5）。</p>
 *
 * <table border="1">
 *   <caption>名字映射</caption>
 *   <tr><th>常量</th><th>Micrometer</th><th>Prometheus 导出名</th></tr>
 *   <tr><td>{@link #LOGIN}</td><td>gateway.session.login.count</td><td>gateway_session_login_count_total</td></tr>
 *   <tr><td>{@link #CATALOG}</td><td>gateway.catalog.lookup.count</td><td>gateway_catalog_lookup_count_total</td></tr>
 *   <tr><td>{@link #RUN}</td><td>gateway.agent.run.count</td><td>gateway_agent_run_count_total</td></tr>
 *   <tr><td>{@link #RUN_DURATION}</td><td>gateway.agent.run.duration</td><td>gateway_agent_run_duration_seconds_*</td></tr>
 *   <tr><td>{@link #UPSTREAM_ERROR}</td><td>gateway.upstream.error.count</td><td>gateway_upstream_error_count_total</td></tr>
 * </table>
 */
public final class GatewayMetricNames {

    /** 登录次数（标签：{@code outcome}）。 */
    public static final String LOGIN = "gateway.session.login.count";

    /** 目录查询次数（标签：{@code cache.hit}）。 */
    public static final String CATALOG = "gateway.catalog.lookup.count";

    /** 经网关的 Agent 执行次数（标签：{@code outcome}）。 */
    public static final String RUN = "gateway.agent.run.count";

    /** 经网关的 Agent 执行耗时（标签：{@code outcome}）。 */
    public static final String RUN_DURATION = "gateway.agent.run.duration";

    /** 上游失败次数（标签：{@code error.code}）。 */
    public static final String UPSTREAM_ERROR = "gateway.upstream.error.count";

    /** 结果标签键。 */
    public static final String TAG_OUTCOME = "outcome";

    /** 缓存命中标签键（值 {@code true} / {@code false}）。 */
    public static final String TAG_CACHE_HIT = "cache.hit";

    /** 上游错误码标签键。 */
    public static final String TAG_ERROR_CODE = "error.code";

    /** 成功结果值。 */
    public static final String OUTCOME_SUCCESS = "SUCCESS";

    /** 失败结果值。 */
    public static final String OUTCOME_FAILURE = "FAILURE";

    /** 未知值占位：避免空标签产生非法时间序列。 */
    public static final String UNKNOWN = "UNKNOWN";

    private GatewayMetricNames() {
    }
}
