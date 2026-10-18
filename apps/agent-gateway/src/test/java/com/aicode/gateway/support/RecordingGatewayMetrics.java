package com.aicode.gateway.support;

import com.aicode.gateway.domain.port.GatewayMetricsPort;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关指标端口的记录式假实现（单测用，Week 20）。
 *
 * <p>只记录「发生了什么」，用于断言用例在正确分支上报了正确标签；
 * 指标真正落到 Micrometer 的行为由 {@code MicrometerGatewayMetricsAdapterTest} 覆盖。</p>
 */
public class RecordingGatewayMetrics implements GatewayMetricsPort {

    /** 调用记录，形如 {@code login:SUCCESS} / {@code catalog:hit} / {@code run:FAILURE}。 */
    public final List<String> calls = new ArrayList<>();

    @Override
    public void sessionLogin(String outcome) {
        calls.add("login:" + outcome);
    }

    @Override
    public void catalogLookup(boolean cacheHit) {
        calls.add("catalog:" + (cacheHit ? "hit" : "miss"));
    }

    @Override
    public void agentRun(String outcome, long durationMs) {
        calls.add("run:" + outcome + ":" + (durationMs >= 0));
    }

    @Override
    public void upstreamFailure(String errorCode) {
        calls.add("upstream:" + errorCode);
    }

    /**
     * 是否记录过某个前缀。
     *
     * @param prefix 前缀
     * @return 存在返回 true
     */
    public boolean saw(String prefix) {
        return calls.stream().anyMatch(call -> call.startsWith(prefix));
    }
}
