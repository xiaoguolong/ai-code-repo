package com.aicode.framework.observability.domain;

/**
 * 一次 span 的生命周期句柄（Week 17）。
 *
 * <p>由 {@link AgentObservabilityPort#openSpan} 创建，调用方用 try-with-resources 保证一定结束
 * （规范要求：span 有开始就必须有结束，异常路径也不例外）。</p>
 *
 * <p>约定：</p>
 * <ul>
 *   <li>{@link #close()} 必须幂等，重复调用不产生第二个 span；</li>
 *   <li>{@link #recordError(Throwable)} 只标记错误，<b>不吞异常</b>，异常仍由调用方继续抛出；</li>
 *   <li>关闭观测（空实现）时本句柄所有方法均为安全空操作，返回空串，调用方无需判空。</li>
 * </ul>
 */
public interface SpanScope extends AutoCloseable {

    /**
     * 写入一个 span 属性。
     *
     * <p><b>隐私红线</b>：只允许标识、模型名、长度、token 数与状态；
     * 禁止 Prompt 正文、模型输出正文、患者姓名 / 身份证 / 手机号、密码、token、API Key。</p>
     *
     * @param key   属性键，建议取 {@link ObservabilityAttributes} 常量
     * @param value 属性值；null 时忽略该属性
     */
    void attribute(String key, String value);

    /**
     * 标记本次 span 失败（状态置 ERROR 并记录异常），不影响异常继续抛出。
     *
     * @param throwable 失败原因，null 时忽略
     */
    void recordError(Throwable throwable);

    /**
     * 当前 span 所属链路的 OTel traceId（32 位小写 hex）。
     *
     * @return traceId；观测关闭时为空串
     */
    String traceId();

    /**
     * 当前 span 的 spanId（16 位小写 hex）。
     *
     * @return spanId；观测关闭时为空串
     */
    String spanId();

    /**
     * 当前链路是否被采样。
     *
     * <p>用于生成 W3C {@code traceparent} 的正确 flags：未采样时下游不应继续上报，
     * 但仍需携带 traceId 以保持链路可关联。</p>
     *
     * @return 采样为 true；观测关闭时为 false
     */
    boolean sampled();

    /**
     * 结束 span 并恢复上下文相关状态。必须幂等。
     */
    @Override
    void close();
}
