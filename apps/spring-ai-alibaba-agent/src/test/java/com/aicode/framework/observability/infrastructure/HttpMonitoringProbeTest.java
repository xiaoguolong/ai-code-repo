package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.StubHttpServer;
import com.aicode.framework.observability.domain.MonitoringProbeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 健康探针测试（Week 20）。
 *
 * <p>探针的价值在于「如实、可缓存、不拖垮自检接口」：</p>
 * <ul>
 *   <li>2xx/3xx → UP；4xx/5xx → DOWN（带状态码，便于判断是配置错还是后端挂）；</li>
 *   <li>连接失败 / 超时 / 非法 URL → DOWN，且<b>不抛异常</b>（自检接口不能因后端挂了而 500）；</li>
 *   <li>结果按 URL 缓存 TTL 秒，避免每次自检都打一轮后端；TTL 到期必须重新探测。</li>
 * </ul>
 */
class HttpMonitoringProbeTest {

    @Test
    @DisplayName("200 → UP，并且真的打到给定路径")
    void twoHundredIsUp() throws Exception {
        try (StubHttpServer server = StubHttpServer.start()) {
            server.respond(200, "application/json", "{\"status\":\"UP\"}");
            HttpMonitoringProbe probe = new HttpMonitoringProbe(1000L, 0L, System::currentTimeMillis);

            MonitoringProbeResult result = probe.probe(server.baseUrl() + "/api/health");

            assertThat(result.reachable()).isTrue();
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(server.lastRequest().path()).isEqualTo("/api/health");
        }
    }

    @Test
    @DisplayName("3xx 视为可达（重定向说明进程在监听），5xx 视为不可达")
    void statusCodesAreClassified() throws Exception {
        try (StubHttpServer server = StubHttpServer.start()) {
            HttpMonitoringProbe probe = new HttpMonitoringProbe(1000L, 0L, System::currentTimeMillis);

            server.respond(302, "text/plain", "");
            assertThat(probe.probe(server.baseUrl() + "/").reachable()).isTrue();

            server.respond(503, "text/plain", "down");
            MonitoringProbeResult result = probe.probe(server.baseUrl() + "/");
            assertThat(result.reachable()).isFalse();
            assertThat(result.statusCode()).isEqualTo(503);
            assertThat(result.detail()).contains("503");
        }
    }

    @Test
    @DisplayName("连接失败 → DOWN，不抛异常")
    void connectionRefusedIsDown() {
        HttpMonitoringProbe probe = new HttpMonitoringProbe(300L, 0L, System::currentTimeMillis);

        MonitoringProbeResult result = probe.probe("http://127.0.0.1:1/api/health");

        assertThat(result.reachable()).isFalse();
        assertThat(result.detail()).isNotBlank();
        // 探测失败信息只留异常类型短名，不允许把完整堆栈塞进响应
        assertThat(result.detail()).doesNotContain("\tat ");
    }

    @Test
    @DisplayName("非法 URL / 空 URL → DOWN，不抛异常")
    void invalidUrlIsDown() {
        HttpMonitoringProbe probe = new HttpMonitoringProbe(300L, 0L, System::currentTimeMillis);

        assertThat(probe.probe("not-a-url").reachable()).isFalse();
        assertThat(probe.probe("   ").reachable()).isFalse();
        assertThat(probe.probe(null).reachable()).isFalse();
    }

    @Test
    @DisplayName("TTL 内复用缓存结果（只打一次后端），TTL 到期重新探测")
    void cachesWithinTtlAndRefreshesAfter() throws Exception {
        try (StubHttpServer server = StubHttpServer.start()) {
            server.respond(200, "application/json", "{\"status\":\"UP\"}");
            AtomicLong now = new AtomicLong(1_000_000L);
            HttpMonitoringProbe probe = new HttpMonitoringProbe(1000L, 60L, now::get);
            String url = server.baseUrl() + "/api/health";

            probe.probe(url);
            probe.probe(url);
            assertThat(server.requests()).hasSize(1);

            now.addAndGet(60_000L);
            probe.probe(url);
            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    @DisplayName("TTL<=0 关闭缓存：每次都真实探测（测试与排障需要）")
    void nonPositiveTtlDisablesCache() throws Exception {
        try (StubHttpServer server = StubHttpServer.start()) {
            server.respond(200, "application/json", "{}");
            HttpMonitoringProbe probe = new HttpMonitoringProbe(1000L, 0L, System::currentTimeMillis);
            String url = server.baseUrl() + "/api/health";

            probe.probe(url);
            probe.probe(url);

            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    @DisplayName("配置对象直接构造探针：超时与 TTL 从 monitoring.probe.* 读取")
    void buildsFromProperties() throws Exception {
        try (StubHttpServer server = StubHttpServer.start()) {
            server.respond(200, "application/json", "{}");
            MonitoringProperties properties = new MonitoringProperties(
                    true,
                    new MonitoringProperties.ProbeConfig(1500L, 5L),
                    null, null, null, null, null);
            HttpMonitoringProbe probe = new HttpMonitoringProbe(properties);

            assertThat(probe.probe(server.baseUrl() + "/x").reachable()).isTrue();
        }
    }
}
