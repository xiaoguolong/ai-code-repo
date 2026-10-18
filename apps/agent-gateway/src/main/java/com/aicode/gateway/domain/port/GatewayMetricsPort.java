package com.aicode.gateway.domain.port;

/**
 * 网关指标上报端口（Week 20）。
 *
 * <p>应用层只依赖本接口，{@code io.micrometer.*} 类型不出现在用例里（规范 3.3）。
 * 实现必须<b>永不抛异常</b>：指标后端故障不得影响业务请求。</p>
 *
 * <p>{@link #NOOP} 供未装配指标的单元测试与关闭场景使用，保证既有构造器行为不变。</p>
 */
public interface GatewayMetricsPort {

    /** 空实现：什么都不记，业务零影响。 */
    GatewayMetricsPort NOOP = new GatewayMetricsPort() {
        @Override
        public void sessionLogin(String outcome) {
            // 空实现
        }

        @Override
        public void catalogLookup(boolean cacheHit) {
            // 空实现
        }

        @Override
        public void agentRun(String outcome, long durationMs) {
            // 空实现
        }

        @Override
        public void upstreamFailure(String errorCode) {
            // 空实现
        }
    };

    /**
     * 记录一次登录结果。
     *
     * @param outcome {@code SUCCESS} / {@code FAILURE}；空值由实现归一为 {@code UNKNOWN}
     */
    void sessionLogin(String outcome);

    /**
     * 记录一次目录查询是否命中缓存。
     *
     * @param cacheHit true 表示命中（未回源平台）
     */
    void catalogLookup(boolean cacheHit);

    /**
     * 记录一次经网关的 Agent 执行。
     *
     * @param outcome    结果（{@code SUCCESS} / {@code FAILURE}）
     * @param durationMs 耗时（毫秒，负数由实现归一为 0）
     */
    void agentRun(String outcome, long durationMs);

    /**
     * 记录一次上游失败。
     *
     * @param errorCode 网关侧错误码（空值由实现归一为 {@code UNKNOWN}）
     */
    void upstreamFailure(String errorCode);
}
