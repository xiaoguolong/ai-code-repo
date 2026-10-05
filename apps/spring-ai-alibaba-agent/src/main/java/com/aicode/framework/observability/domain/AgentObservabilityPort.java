package com.aicode.framework.observability.domain;

import com.aicode.core.domain.model.TokenUsage;

import java.util.Map;

/**
 * 观测能力出站端口（Week 17）。
 *
 * <p>领域与应用层<b>只依赖本接口</b>：不出现 {@code io.opentelemetry.*} 或 {@code io.micrometer.*} 类型，
 * 换后端（SkyWalking / 自研 / Langfuse）只换适配器（规范 3.2、3.3）。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *   <li>永不抛出异常：可观测故障不得影响主流程（与 Week 16 审计同口径）；</li>
 *   <li>{@code observability.enabled=false} 时装配空实现，所有方法为空操作、零开销；</li>
 *   <li>指标标签只允许低基数值（agent.key / 模型名 / 状态 / 工具名 / token 类型），
 *       禁止 executionId、userId、patientId 这类高基数值（否则时间序列爆炸）。</li>
 * </ul>
 */
public interface AgentObservabilityPort {

    /**
     * 开始一个 span。调用方必须用 try-with-resources 或 finally 关闭返回值。
     *
     * @param kind        span 种类
     * @param name        span 名（链路后端按此检索，如 {@code agent.run}）
     * @param attributes  初始属性；key/value 为 null 的条目应被忽略
     * @return span 句柄，永不为 null
     */
    SpanScope openSpan(SpanKind kind, String name, Map<String, String> attributes);

    /**
     * 进入一个 Langfuse trace 维度作用域（Week 18）。
     *
     * <p>维度（用户 / 会话 / trace 名 / 标签）在请求处理过程中才可知，而 Langfuse 官方要求它们
     * 出现在 trace 内<b>每个</b> span 上；实现把维度登记到请求期持有者，由 {@code SpanProcessor}
     * 在 span 开始时写到 span 属性。调用方必须用 try-with-resources 保证退出作用域，
     * 否则 Tomcat 线程复用会把上一个请求的维度带到下一个请求。</p>
     *
     * <p>观测关闭或传入 null 时返回空操作句柄，调用方无需判空。</p>
     *
     * @param dimensions trace 维度，可为 null
     * @return 作用域句柄，永不为 null
     */
    TraceScope beginTrace(TraceDimensions dimensions);

    /**
     * 记录一次大模型调用的真实 token 用量（提示 / 补全两类计数）。
     *
     * <p>上游未返回 usage 时 {@code usage} 全为 0，此时<b>不记录</b>，避免把未知值混入成本统计。</p>
     *
     * @param model 模型名，空则记为 {@code unknown}
     * @param usage 用量，可为 null
     */
    void recordTokenUsage(String model, TokenUsage usage);

    /**
     * 计数型指标自增。
     *
     * @param name   指标名，空则忽略
     * @param amount 增量
     * @param tags   标签键值对（交替出现），奇数个时末位忽略
     */
    void recordCounter(String name, double amount, String... tags);

    /**
     * 耗时型指标记录（单位毫秒，由适配器换算为秒）。
     *
     * @param name      指标名，空则忽略
     * @param durationMs 耗时毫秒
     * @param tags      标签键值对（交替出现），奇数个时末位忽略
     */
    void recordDuration(String name, long durationMs, String... tags);

    /**
     * 观测是否启用。
     *
     * @return 启用的适配器返回 true，空实现返回 false
     */
    boolean isEnabled();

    /**
     * 读取调用链上当前 span 的链路上下文。
     *
     * <p>用于自检接口把「日志 traceId」与「OTel traceId / spanId」对照展示。
     * 无活动 span（例如观测关闭或非请求线程）时返回 {@link TraceContextView#disabled} 形状的空值。</p>
     *
     * @param traceId 自研 traceId（由调用方从 MDC 提供），原样回填到视图
     * @return 链路上下文视图，永不为 null
     */
    TraceContextView currentTraceContext(String traceId);

    /**
     * 读取调用链上当前 span 的 spanId（16 位小写 hex）。
     *
     * <p>用于在响应回写 W3C {@code traceparent}：框架自动埋点（如 Spring MVC 观测）
     * 已经建立 server span 时，必须沿用它的 spanId，而不是另开一个 span ——
     * 否则链路后端会出现业务入口的两个兄弟 server span。</p>
     *
     * @return 当前 spanId；无活动 span 或观测关闭时为空串
     */
    String activeSpanId();
}
