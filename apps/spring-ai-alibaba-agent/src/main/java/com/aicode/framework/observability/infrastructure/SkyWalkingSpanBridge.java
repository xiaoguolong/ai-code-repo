package com.aicode.framework.observability.infrastructure;

/**
 * SkyWalking 手动埋点桥（Week 19）。
 *
 * <p>为什么要有这层接口：SkyWalking Toolkit 的类<b>只存在于 Agent 挂载时</b>。
 * 领域与适配器若直接依赖 {@code org.apache.skywalking.*}，一旦忘记挂 Agent 就是
 * {@code NoClassDefFoundError} 起不来。本接口把「能力」与「实现」分开：</p>
 * <ul>
 *   <li>{@link ToolkitSpanBridge}：真实实现，用<b>反射</b>调用 Toolkit 静态方法，
 *       未挂 Agent 时退化为 {@link NoopSpanBridge}（启动打 WARN，而不是崩）；</li>
 *   <li>{@link NoopSpanBridge}：空实现，所有方法为空操作 / 返回空串，零开销。</li>
 * </ul>
 *
 * <p>约定：任何方法都不得抛异常（可观测故障不得影响业务，与 Week 17/18 同口径）。</p>
 */
public interface SkyWalkingSpanBridge {

    /**
     * 能力是否可用（Agent 已挂载且 Toolkit 类可加载）。
     *
     * @return 可用返回 true
     */
    boolean available();

    /**
     * 当前 SkyWalking traceId（Base64 segmentId）。
     *
     * @return traceId；无活动链路或不可用时为空串
     */
    String currentTraceId();

    /**
     * 当前 SkyWalking spanId。
     *
     * @return spanId；不可用时返回 -1
     */
    int currentSpanId();

    /**
     * 开始一个本地 span（成为当前活动 span）。
     *
     * @param operationName 操作名（链路后端按此检索，如 {@code agent.run}）
     * @return span 句柄，永不为 null
     */
    SkyWalkingSpanHandle startLocalSpan(String operationName);

    /**
     * 给<b>当前活动 span</b> 写标签。
     *
     * <p>Toolkit 的 {@code ActiveSpan.tag} 不绑定句柄而是作用于当前活动 span，
     * 因此只有「在 span 生命周期内、同一线程」的标签才写得进去 —— 这是 Toolkit 的
     * 硬约束，不是本项目的取舍。</p>
     *
     * @param key   标签键
     * @param value 标签值
     */
    void tagActiveSpan(String key, String value);

    /**
     * 标记当前活动 span 失败。
     *
     * @param message 失败说明（不含敏感信息）
     */
    void errorActiveSpan(String message);

    /**
     * 结束当前 span（与 {@link #startLocalSpan(String)} 配对）。
     */
    void stopSpan();

    /**
     * SkyWalking span 句柄。
     */
    interface SkyWalkingSpanHandle extends AutoCloseable {

        /**
         * 结束 span，必须幂等。
         */
        @Override
        void close();
    }
}
