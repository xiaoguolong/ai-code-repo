package com.aicode.gateway.controller;

import com.aicode.gateway.support.GatewayTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关 Prometheus 出口端到端测试（Week 20）。
 *
 * <p>第 19 周网关只暴露 {@code health,info}：网关的 HTTP/Feign/JVM 指标只在 SkyWalking 里可见，
 * Prometheus 抓不到。本周打开指标出口后，本测试用真实 WebFlux + Actuator 管线验证
 * 「端点可匿名抓取 + 带 application 公共标签 + 业务指标真的被登录流程写进去」。</p>
 *
 * <p>{@code @AutoConfigureObservability} 必须加：{@code @SpringBootTest} 默认会关掉
 * metrics export（{@code management.defaults.metrics.export.enabled=false}），
 * 那样 Prometheus 注册表与端点都不会装配，测试会得到 404（实测踩过）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ActiveProfiles("test")
@Import(GatewayTestConfiguration.class)
class GatewayPrometheusEndpointTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("/actuator/prometheus 可抓取，含 JVM 指标与 application 公共标签")
    void prometheusEndpointIsScrapable() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
        assertThat(response.getBody()).contains("application=\"agent-gateway\"");
    }

    @Test
    @DisplayName("登录会写入 gateway_session_login_count_total（Grafana 网关面板取这个名字）")
    void loginWritesGatewayMetric() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity(
                "/api/v1/gateway/sessions",
                new HttpEntity<>(Map.of("username", "admin", "password", "admin123"), headers),
                String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> metrics = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getBody()).contains("gateway_session_login_count_total");
        assertThat(metrics.getBody()).contains("outcome=\"SUCCESS\"");
    }
}
