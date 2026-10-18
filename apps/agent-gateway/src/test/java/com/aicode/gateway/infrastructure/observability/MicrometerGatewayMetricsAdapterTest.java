package com.aicode.gateway.infrastructure.observability;

import com.aicode.gateway.domain.model.GatewayMetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关指标适配器测试（Week 20）。
 *
 * <p>第 19 周网关只有 {@code health,info} 两个 Actuator 端点，HTTP/Feign/JVM 指标只进了
 * SkyWalking，Prometheus 侧完全看不到网关。本周补上指标出口后，这里锁死四个自有指标的
 * <b>名字、标签、计数</b>，Grafana 网关面板与告警都按这些名字取值。</p>
 */
class MicrometerGatewayMetricsAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerGatewayMetricsAdapter adapter = new MicrometerGatewayMetricsAdapter(registry);

    @Test
    @DisplayName("登录：按 outcome 累计")
    void recordsLoginOutcome() {
        adapter.sessionLogin("SUCCESS");
        adapter.sessionLogin("SUCCESS");
        adapter.sessionLogin("FAILURE");

        assertThat(registry.get(GatewayMetricNames.LOGIN)
                .tag(GatewayMetricNames.TAG_OUTCOME, "SUCCESS").counter().count()).isEqualTo(2.0);
        assertThat(registry.get(GatewayMetricNames.LOGIN)
                .tag(GatewayMetricNames.TAG_OUTCOME, "FAILURE").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("目录查询：cache.hit=true/false 两条序列（算命中率）")
    void recordsCatalogCacheHit() {
        adapter.catalogLookup(true);
        adapter.catalogLookup(false);
        adapter.catalogLookup(false);

        assertThat(registry.get(GatewayMetricNames.CATALOG)
                .tag(GatewayMetricNames.TAG_CACHE_HIT, "true").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(GatewayMetricNames.CATALOG)
                .tag(GatewayMetricNames.TAG_CACHE_HIT, "false").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("执行：次数 + 耗时，按 outcome 分标签")
    void recordsRunCountAndDuration() {
        adapter.agentRun("SUCCESS", 1234L);
        adapter.agentRun("FAILURE", -5L);

        assertThat(registry.get(GatewayMetricNames.RUN)
                .tag(GatewayMetricNames.TAG_OUTCOME, "SUCCESS").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(GatewayMetricNames.RUN_DURATION)
                .tag(GatewayMetricNames.TAG_OUTCOME, "SUCCESS").timer().count()).isEqualTo(1L);
        // 负耗时归一为 0，不能把 Timer 写崩
        assertThat(registry.get(GatewayMetricNames.RUN_DURATION)
                .tag(GatewayMetricNames.TAG_OUTCOME, "FAILURE").timer().totalTime(TimeUnit.MILLISECONDS))
                .isZero();
    }

    @Test
    @DisplayName("上游失败：按错误码累计；空码归一为 UNKNOWN，避免空标签")
    void recordsUpstreamFailureByCode() {
        adapter.upstreamFailure("GATEWAY_UPSTREAM_ERROR");
        adapter.upstreamFailure("");
        adapter.upstreamFailure(null);

        assertThat(registry.get(GatewayMetricNames.UPSTREAM_ERROR)
                .tag(GatewayMetricNames.TAG_ERROR_CODE, "GATEWAY_UPSTREAM_ERROR").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(GatewayMetricNames.UPSTREAM_ERROR)
                .tag(GatewayMetricNames.TAG_ERROR_CODE, "UNKNOWN").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("outcome 归一：空值记 UNKNOWN，绝不产生空标签")
    void normalizesBlankOutcome() {
        adapter.sessionLogin(null);
        adapter.sessionLogin("  ");
        adapter.agentRun(null, 1L);

        assertThat(registry.get(GatewayMetricNames.LOGIN)
                .tag(GatewayMetricNames.TAG_OUTCOME, "UNKNOWN").counter().count()).isEqualTo(2.0);
        assertThat(registry.get(GatewayMetricNames.RUN)
                .tag(GatewayMetricNames.TAG_OUTCOME, "UNKNOWN").counter().count()).isEqualTo(1.0);
    }
}
