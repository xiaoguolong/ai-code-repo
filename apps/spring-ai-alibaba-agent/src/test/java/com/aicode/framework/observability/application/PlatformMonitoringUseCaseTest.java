package com.aicode.framework.observability.application;

import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.observability.LangfuseTestFixtures;
import com.aicode.framework.observability.domain.MonitoringComponent;
import com.aicode.framework.observability.domain.MonitoringOverviewView;
import com.aicode.framework.observability.domain.MonitoringProbePort;
import com.aicode.framework.observability.domain.MonitoringProbeResult;
import com.aicode.framework.observability.domain.MonitoringStatus;
import com.aicode.framework.observability.infrastructure.MonitoringProperties;
import com.aicode.framework.observability.infrastructure.NoopSpanBridge;
import com.aicode.framework.observability.infrastructure.SkyWalkingProperties;
import com.aicode.framework.observability.infrastructure.SkyWalkingStatusProvider;
import com.aicode.framework.observability.support.RecordingObservabilityPort;
import com.aicode.framework.platform.domain.exception.PlatformAccessDeniedException;
import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 统一监控概览用例测试（Week 20）。
 *
 * <p>本项目到第 19 周已有四个观测后端（OTel / Prometheus 指标 / SkyWalking / Langfuse），
 * 但每个都要单独调一个自检接口才知道活没活。本周把它们收进一张「监控体检表」：
 * 每个后端一条记录（开关 / 状态 / 端点 / 控制台链接），外加跨后端关联信息。</p>
 *
 * <p>测试锁死的语义：</p>
 * <ul>
 *   <li>关闭的组件必须是 {@code DISABLED} 而<b>不是</b> {@code DOWN}——把「没开」报成「坏了」会制造假故障；</li>
 *   <li>只要有一个「已启用」的组件不可达，整体就不健康（{@code allHealthy=false}）；</li>
 *   <li>概览里<b>不许出现 URL 里的账号密码</b>（配置里可能带凭据）；</li>
 *   <li>只有管理员能看（端点会暴露内部地址与拓扑信息）。</li>
 * </ul>
 */
class PlatformMonitoringUseCaseTest {

    private final RecordingObservabilityPort observability = new RecordingObservabilityPort();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("全部可达：六个组件齐全，OTel/Prometheus/Grafana/网关指标 UP，未启用的后端为 DISABLED")
    void allReachableComponentsAreUp() {
        PlatformMonitoringUseCase useCase = useCase(true, allUpProbe(new AtomicInteger()), LangfuseTestFixtures.disabled());

        MonitoringOverviewView view = useCase.overview(1L);
        Map<String, MonitoringComponent> byKey = byKey(view);

        assertThat(byKey.keySet()).containsExactly(
                "otel", "prometheus", "grafana", "skywalking", "langfuse", "gateway-metrics");
        assertThat(byKey.get("otel").status()).isEqualTo(MonitoringStatus.UP);
        assertThat(byKey.get("prometheus").status()).isEqualTo(MonitoringStatus.UP);
        assertThat(byKey.get("grafana").status()).isEqualTo(MonitoringStatus.UP);
        assertThat(byKey.get("gateway-metrics").status()).isEqualTo(MonitoringStatus.UP);
        assertThat(byKey.get("skywalking").status()).isEqualTo(MonitoringStatus.DISABLED);
        assertThat(byKey.get("langfuse").status()).isEqualTo(MonitoringStatus.DISABLED);
        assertThat(view.allHealthy()).isTrue();
        assertThat(view.generatedAt()).isNotNull();
    }

    @Test
    @DisplayName("有一个已启用后端不可达 → 该组件 DOWN，整体不健康（关闭的组件不参与判定）")
    void oneDownMakesOverviewUnhealthy() {
        AtomicInteger probes = new AtomicInteger();
        MonitoringProbePort probe = url -> {
            probes.incrementAndGet();
            if (url.contains("19090")) {
                return MonitoringProbeResult.down("connect refused");
            }
            return MonitoringProbeResult.up(200);
        };
        PlatformMonitoringUseCase useCase = useCase(true, probe, LangfuseTestFixtures.disabled());

        MonitoringOverviewView view = useCase.overview(1L);
        Map<String, MonitoringComponent> byKey = byKey(view);

        assertThat(byKey.get("prometheus").status()).isEqualTo(MonitoringStatus.DOWN);
        assertThat(byKey.get("prometheus").detail()).contains("connect refused");
        assertThat(byKey.get("grafana").status()).isEqualTo(MonitoringStatus.UP);
        assertThat(view.allHealthy()).isFalse();
        assertThat(probes.get()).isEqualTo(3); // prometheus + grafana + gateway-metrics
    }

    @Test
    @DisplayName("SkyWalking 开了手动埋点但 Agent 没挂：报 DOWN（配置与事实不一致必须暴露）")
    void skyWalkingEnabledWithoutAgentIsDown() {
        RecordingObservabilityPort port = new RecordingObservabilityPort();
        SkyWalkingProperties skyWalking = new SkyWalkingProperties(true, "ai-code-platform", "127.0.0.1:11800", "aicode");
        SkyWalkingStatusProvider provider =
                new SkyWalkingStatusProvider(port, skyWalking, NoopSpanBridge.INSTANCE);
        PlatformMonitoringUseCase useCase = new PlatformMonitoringUseCase(
                port, LangfuseTestFixtures.disabled(), provider, skyWalking, properties(true),
                allUpProbe(new AtomicInteger()), adminChecker());

        MonitoringComponent skyWalkingComponent = byKey(useCase.overview(1L)).get("skywalking");

        assertThat(skyWalkingComponent.enabled()).isTrue();
        assertThat(skyWalkingComponent.status()).isEqualTo(MonitoringStatus.DOWN);
        assertThat(skyWalkingComponent.detail()).contains("agentAttached=false");
    }

    @Test
    @DisplayName("Langfuse 开启时真的去探 /api/public/health；关闭时零网络请求")
    void langfuseProbeFollowsEnabledFlag() {
        AtomicInteger langfuseProbes = new AtomicInteger();
        MonitoringProbePort probe = url -> {
            if (url.contains("langfuse")) {
                langfuseProbes.incrementAndGet();
            }
            return MonitoringProbeResult.up(200);
        };
        PlatformMonitoringUseCase enabledUseCase = useCase(
                true, probe, LangfuseTestFixtures.enabledAt("http://langfuse.local:3000"));
        PlatformMonitoringUseCase disabledUseCase = useCase(
                true, probe, LangfuseTestFixtures.disabled());

        MonitoringComponent enabled = byKey(enabledUseCase.overview(1L)).get("langfuse");
        assertThat(enabled.status()).isEqualTo(MonitoringStatus.UP);
        assertThat(enabled.endpoint()).isEqualTo("http://langfuse.local:3000");
        assertThat(enabled.consoleUrl()).isEqualTo("http://langfuse.local:3000");
        assertThat(langfuseProbes.get()).isEqualTo(1);

        MonitoringComponent disabled = byKey(disabledUseCase.overview(1L)).get("langfuse");
        assertThat(disabled.status()).isEqualTo(MonitoringStatus.DISABLED);
        // 关闭后不再探 Langfuse（Prometheus/Grafana/网关指标仍会探，故只看 langfuse 计数）
        assertThat(langfuseProbes.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("URL 中的凭据被剥离（配置可能写成 http://user:pass@host，响应里不许回显）")
    void credentialsAreStrippedFromEndpoints() {
        SkyWalkingProperties skyWalkingProperties = new SkyWalkingProperties(false, "", "", "aicode");
        PlatformMonitoringUseCase useCase = new PlatformMonitoringUseCase(
                observability, LangfuseTestFixtures.disabled(),
                new SkyWalkingStatusProvider(observability, skyWalkingProperties, NoopSpanBridge.INSTANCE),
                skyWalkingProperties,
                new MonitoringProperties(
                        true,
                        new MonitoringProperties.ProbeConfig(300L, 0L),
                        new MonitoringProperties.PrometheusConfig("http://prom-user:prom-pass@127.0.0.1:19090", null, null),
                        new MonitoringProperties.GrafanaConfig("http://graf-user:graf-pass@127.0.0.1:3001", null),
                        new MonitoringProperties.OtelConfig("http://otel-user:otel-pass@collector:4318/v1/traces", 1.0d, true),
                        new MonitoringProperties.LinksConfig("http://sw-user:sw-pass@127.0.0.1:8088"),
                        new MonitoringProperties.TargetsConfig("http://gw-user:gw-pass@127.0.0.1:8080/actuator/prometheus")),
                allUpProbe(new AtomicInteger()),
                adminChecker());

        String serialized = useCase.overview(1L).components().toString();

        assertThat(serialized).doesNotContain("prom-pass", "graf-pass", "otel-pass", "sw-pass", "gw-pass");
        assertThat(serialized).contains("127.0.0.1:19090", "127.0.0.1:3001");
    }

    @Test
    @DisplayName("monitoring.enabled=false：Prometheus/Grafana/网关指标一律 DISABLED，且不发起任何探测")
    void disabledMonitoringSkipsProbes() {
        AtomicInteger probes = new AtomicInteger();
        PlatformMonitoringUseCase useCase = useCase(false, allUpProbe(probes), LangfuseTestFixtures.disabled());

        Map<String, MonitoringComponent> byKey = byKey(useCase.overview(1L));

        assertThat(probes.get()).isZero();
        assertThat(byKey.get("prometheus").status()).isEqualTo(MonitoringStatus.DISABLED);
        assertThat(byKey.get("grafana").status()).isEqualTo(MonitoringStatus.DISABLED);
        assertThat(byKey.get("gateway-metrics").status()).isEqualTo(MonitoringStatus.DISABLED);
        // OTel / SkyWalking / Langfuse 是既有后端，与「本周监控探测开关」无关
        assertThat(byKey.get("otel").status()).isEqualTo(MonitoringStatus.UP);
    }

    @Test
    @DisplayName("关联块：业务 traceId、OTel traceId、关联标签、Langfuse 深链与 Grafana/SkyWalking 入口")
    void correlationLinksAllBackends() {
        MDC.put(TraceIds.MDC_KEY, "week20-e2e");
        PlatformMonitoringUseCase useCase = useCase(
                true, allUpProbe(new AtomicInteger()), LangfuseTestFixtures.enabledAt("http://langfuse.local:3000"));

        MonitoringOverviewView.Correlation correlation = useCase.overview(1L).correlation();

        assertThat(correlation.traceId()).isEqualTo("week20-e2e");
        assertThat(correlation.otelTraceId()).isEqualTo("otel-trace");
        assertThat(correlation.correlationTag()).isEqualTo("aicode.trace_id");
        assertThat(correlation.langfuseTraceUrl())
                .contains("http://langfuse.local:3000", "otel-trace");
        assertThat(correlation.grafanaDashboardUrl())
                .isEqualTo("http://127.0.0.1:3001/d/aicode-platform-overview");
        assertThat(correlation.skywalkingUiUrl()).isEqualTo("http://127.0.0.1:8088");
        assertThat(correlation.queryHints()).containsKeys("logs", "audit", "skywalking", "langfuse");
        assertThat(correlation.queryHints().get("logs")).contains("week20-e2e");
    }

    @Test
    @DisplayName("非管理员 403：概览暴露内部地址与拓扑，不能给普通用户看")
    void nonAdminIsDenied() {
        PlatformMonitoringUseCase useCase = useCase(true, allUpProbe(new AtomicInteger()), LangfuseTestFixtures.disabled());

        assertThatThrownBy(() -> useCase.overview(2L))
                .isInstanceOf(PlatformAccessDeniedException.class)
                .hasMessageContaining("admin");
    }

    private PlatformMonitoringUseCase useCase(
            boolean monitoringEnabled, MonitoringProbePort probe, com.aicode.framework.observability.infrastructure.LangfuseProperties langfuse) {
        SkyWalkingProperties skyWalking = new SkyWalkingProperties(false, "", "", "aicode");
        return new PlatformMonitoringUseCase(
                observability,
                langfuse,
                new SkyWalkingStatusProvider(observability, skyWalking, NoopSpanBridge.INSTANCE),
                skyWalking,
                properties(monitoringEnabled),
                probe,
                adminChecker());
    }

    private MonitoringProperties properties(boolean enabled) {
        return new MonitoringProperties(
                enabled,
                new MonitoringProperties.ProbeConfig(300L, 0L),
                new MonitoringProperties.PrometheusConfig("http://127.0.0.1:19090", "/actuator/prometheus", "/-/ready"),
                new MonitoringProperties.GrafanaConfig("http://127.0.0.1:3001", "aicode-platform-overview"),
                new MonitoringProperties.OtelConfig("http://localhost:4318/v1/traces", 1.0d, true),
                new MonitoringProperties.LinksConfig("http://127.0.0.1:8088"),
                new MonitoringProperties.TargetsConfig("http://127.0.0.1:8080/actuator/prometheus"));
    }

    private MonitoringProbePort allUpProbe(AtomicInteger counter) {
        return url -> {
            counter.incrementAndGet();
            return MonitoringProbeResult.up(200);
        };
    }

    private PlatformPermissionChecker adminChecker() {
        PlatformPermissionChecker checker = mock(PlatformPermissionChecker.class);
        when(checker.isAdmin(1L)).thenReturn(true);
        when(checker.isAdmin(2L)).thenReturn(false);
        return checker;
    }

    private Map<String, MonitoringComponent> byKey(MonitoringOverviewView view) {
        return view.components().stream()
                .collect(Collectors.toMap(MonitoringComponent::key, Function.identity(),
                        (left, right) -> left, java.util.LinkedHashMap::new));
    }
}
