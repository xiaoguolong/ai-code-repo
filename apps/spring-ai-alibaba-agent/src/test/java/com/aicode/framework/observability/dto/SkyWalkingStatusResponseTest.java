package com.aicode.framework.observability.dto;

import com.aicode.framework.observability.domain.SkyWalkingStatusView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkyWalking 自检响应测试（Week 19）。
 *
 * <p>响应体是「类型上就没有密钥」的形状，测试同时锁死字段映射与 correlated 语义，
 * 避免将来加字段时把 Agent 侧的配置（如 OAP 地址里的凭据）误带出来。</p>
 */
class SkyWalkingStatusResponseTest {

    @Test
    @DisplayName("视图 → 响应逐字段映射")
    void mapsAllFields() {
        SkyWalkingStatusView view = new SkyWalkingStatusView(
                true, true, "ai-code-platform", "192.168.132.128:11800",
                "c2t5YWxraW5n.1.1", 5, "week19-e2e", "4bf92f3577b34da6a3ce929d0e0e4736", "aicode.trace_id");

        SkyWalkingStatusResponse response = SkyWalkingStatusResponse.from(view);

        assertThat(response.enabled()).isTrue();
        assertThat(response.bridgeAvailable()).isTrue();
        assertThat(response.serviceName()).isEqualTo("ai-code-platform");
        assertThat(response.backendService()).isEqualTo("192.168.132.128:11800");
        assertThat(response.skywalkingTraceId()).isEqualTo("c2t5YWxraW5n.1.1");
        assertThat(response.skywalkingSpanId()).isEqualTo(5);
        assertThat(response.traceId()).isEqualTo("week19-e2e");
        assertThat(response.otelTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(response.correlationTag()).isEqualTo("aicode.trace_id");
        assertThat(response.correlated()).isTrue();
    }

    @Test
    @DisplayName("两个 ID 只要缺一个，correlated=false（不谎报已关联）")
    void correlatedRequiresBothIds() {
        SkyWalkingStatusView onlyBusiness = new SkyWalkingStatusView(
                false, false, "s", "", "", -1, "week19", "", "aicode.trace_id");
        assertThat(onlyBusiness.correlated()).isFalse();

        SkyWalkingStatusView onlySkyWalking = new SkyWalkingStatusView(
                true, true, "s", "", "sw-trace", 1, "", "", "aicode.trace_id");
        assertThat(onlySkyWalking.correlated()).isFalse();

        SkyWalkingStatusView none = new SkyWalkingStatusView(
                false, false, "s", "", "", -1, "", "", "aicode.trace_id");
        assertThat(none.correlated()).isFalse();
    }
}
