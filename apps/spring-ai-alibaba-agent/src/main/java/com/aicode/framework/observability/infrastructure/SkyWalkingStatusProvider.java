package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.SkyWalkingStatusView;
import com.aicode.framework.observability.domain.TraceContextView;
import org.springframework.stereotype.Component;

/**
 * SkyWalking 自检装配（Week 19）。
 *
 * <p>把「当前是否装配了 SkyWalking 装饰器」这一运行时事实与配置信息合并成
 * {@link SkyWalkingStatusView}。之所以单独成类而不写在用例里：用例不该关心端口的具体实现类型，
 * {@code instanceof} 判断属于基础设施知识（规范 5.2）。</p>
 */
@Component
public class SkyWalkingStatusProvider {

    /** 跨后端关联标签名（与装饰器写入的标签一致）。 */
    static final String CORRELATION_TAG_SUFFIX = ".trace_id";

    private final AgentObservabilityPort observability;
    private final SkyWalkingProperties properties;
    private final SkyWalkingSpanBridge bridge;

    /**
     * @param observability 观测端口（可能是装饰器，也可能是既有实现）
     * @param properties    配置
     * @param bridge        SkyWalking 桥
     */
    public SkyWalkingStatusProvider(
            AgentObservabilityPort observability, SkyWalkingProperties properties, SkyWalkingSpanBridge bridge) {
        this.observability = observability;
        this.properties = properties;
        this.bridge = bridge;
    }

    /**
     * 组装自检视图。
     *
     * @return 自检视图，永不为 null
     */
    public SkyWalkingStatusView status() {
        String traceId = TraceIds.current();
        TraceContextView context = observability.currentTraceContext(traceId);
        boolean decorated = observability instanceof SkyWalkingObservabilityAdapter;
        String skyWalkingTraceId = decorated
                ? ((SkyWalkingObservabilityAdapter) observability).skyWalkingTraceId()
                : "";
        int skyWalkingSpanId = decorated
                ? ((SkyWalkingObservabilityAdapter) observability).skyWalkingSpanId()
                : -1;
        return new SkyWalkingStatusView(
                properties.enabled() && decorated,
                bridge.available(),
                properties.resolvedServiceName(),
                properties.resolvedBackendService(),
                skyWalkingTraceId,
                skyWalkingSpanId,
                traceId,
                context.otelTraceId(),
                properties.resolvedTagPrefix() + CORRELATION_TAG_SUFFIX);
    }
}
