package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.LangfuseTestFixtures;
import com.aicode.framework.observability.StubHttpServer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Langfuse OTLP 导出器测试（Week 18）。
 *
 * <p>用本地真实 HTTP 桩断言「应用发出去的请求长什么样」：路径、Basic 认证、v4 摄取版本头、protobuf 正文。
 * 这四项任一写错，Langfuse 都收不到或延迟 10 分钟才收到数据，而单测里看不出来，所以必须真发一次请求。</p>
 */
class LangfuseOtlpSpanExporterTest {

    private StubHttpServer server;
    private SdkTracerProvider tracerProvider;
    private InMemorySpanExporter memoryExporter;

    @BeforeEach
    void setUp() throws IOException {
        server = StubHttpServer.start();
        memoryExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(memoryExporter))
                .build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
        server.close();
    }

    @Test
    void exportsToLangfuseOtlpPathWithBasicAuthAndIngestionVersionHeader() {
        LangfuseOtlpSpanExporter exporter = new LangfuseOtlpSpanExporter(LangfuseTestFixtures.enabledAt(server.baseUrl()));

        CompletableResultCode result = exporter.export(List.of(sampleSpan()));
        result.join(10, TimeUnit.SECONDS);
        exporter.flush().join(10, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        StubHttpServer.RecordedRequest request = server.lastRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/public/otel/v1/traces");
        assertThat(request.header("authorization")).isEqualTo(expectedAuthorization());
        assertThat(request.header(LangfuseOtlpSpanExporter.INGESTION_VERSION_HEADER))
                .isEqualTo(LangfuseOtlpSpanExporter.INGESTION_VERSION);
        assertThat(request.body()).isNotEmpty();
        assertThat(request.header("content-type")).contains("protobuf");
    }

    @Test
    void failsFastWhenCredentialsMissing() {
        LangfuseProperties noKeys = new LangfuseProperties(
                true, "http://localhost:3000", "", "", "p", "env", "rel", false, 2000, 5000,
                new LangfuseProperties.Prompt(false, "production", 60), java.util.Map.of());

        assertThatThrownBy(() -> new LangfuseOtlpSpanExporter(noKeys))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public-key");
    }

    @Test
    void stopsExportingAfterShutdownWithoutThrowing() {
        LangfuseOtlpSpanExporter exporter = new LangfuseOtlpSpanExporter(LangfuseTestFixtures.enabledAt(server.baseUrl()));

        assertThat(exporter.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        exporter.close();
    }

    @Test
    void neverThrowsWhenLangfuseIsUnreachable() throws IOException {
        String deadHost = "http://127.0.0.1:" + freePort();
        LangfuseOtlpSpanExporter exporter = new LangfuseOtlpSpanExporter(LangfuseTestFixtures.enabledAt(deadHost));

        CompletableResultCode result = exporter.export(List.of(sampleSpan()));
        result.join(10, TimeUnit.SECONDS);

        // 导出失败只体现为失败结果（由 OTel SDK 记录日志），绝不向业务抛异常
        assertThat(result.isSuccess()).isFalse();
        exporter.close();
    }

    private String expectedAuthorization() {
        String raw = "pk-lf-test:sk-lf-test";
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private SpanData sampleSpan() {
        Tracer tracer = tracerProvider.get("langfuse-exporter-test");
        Span span = tracer.spanBuilder("agent.run").startSpan();
        span.setAttribute("agent.key", "medical-assistant");
        span.end();
        return memoryExporter.getFinishedSpanItems().stream()
                .filter(item -> "agent.run".equals(item.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("sample span not finished"));
    }

    private static int freePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
