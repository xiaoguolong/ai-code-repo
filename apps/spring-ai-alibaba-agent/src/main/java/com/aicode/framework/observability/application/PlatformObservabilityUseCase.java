package com.aicode.framework.observability.application;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.TraceContextView;
import org.springframework.stereotype.Service;

/**
 * 链路上下文自检用例（Week 17）。
 *
 * <p>把「日志 / 审计 / 响应体里的 traceId」与「链路后端的 OTel traceId + spanId」一次返回，
 * 便于运维与开发核对同一条链路（第 18 周 Langfuse 接入也复用该 traceId 关联）。</p>
 */
@Service
public class PlatformObservabilityUseCase {

    private final AgentObservabilityPort observability;

    public PlatformObservabilityUseCase(AgentObservabilityPort observability) {
        this.observability = observability;
    }

    /**
     * 读取当前请求的链路上下文。
     *
     * @return 链路上下文视图；观测关闭时除 {@code traceId} 外均为空
     */
    public TraceContextView currentTraceContext() {
        return observability.currentTraceContext(TraceIds.current());
    }
}
