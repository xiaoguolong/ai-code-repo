package com.aicode.framework.observability.infrastructure;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Locale;

/**
 * Langfuse OTLP 导出器工厂（Week 18）。
 *
 * <p>按 {@link LangfuseProperties} 构建指向 Langfuse 的 OTLP/HTTP 导出器：
 * 端点 {@code <host>/api/public/otel/v1/traces}、Basic 认证头、{@code x-langfuse-ingestion-version: 4}。
 * 版本头不加会让直连 OTel 数据走延迟路径（最多 10 分钟才可见），因此必须带。</p>
 *
 * <p>返回类型声明为 {@link SpanExporter}：调用方（{@code LangfuseOtlpSpanExporter}）会把它包成自定义类型，
 * 从而<b>不</b>触发 Spring Boot 对 {@code OtlpHttpSpanExporter} 的退避条件，保住既有 collector 导出。</p>
 */
public final class LangfuseOtlpSpanExporterFactory {

    private LangfuseOtlpSpanExporterFactory() {
    }

    /**
     * 创建指向 Langfuse 的 OTLP 导出器。
     *
     * @param properties Langfuse 配置（密钥必填，缺失时抛配置错误）
     * @return OTLP/HTTP span 导出器
     * @throws IllegalStateException 缺少 public key / secret key 时
     */
    public static SpanExporter create(LangfuseProperties properties) {
        properties.requireCredentials();
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(properties.otlpEndpoint())
                .setTimeout(java.time.Duration.ofMillis(properties.resolvedTimeoutMs()))
                .setConnectTimeout(java.time.Duration.ofMillis(properties.resolvedTimeoutMs()))
                .setCompression("gzip")
                .addHeader("Authorization", properties.authorizationHeader())
                .addHeader(LangfuseOtlpSpanExporter.INGESTION_VERSION_HEADER,
                        LangfuseOtlpSpanExporter.INGESTION_VERSION)
                .build();
        return exporter;
    }

    /** 协议族标识（仅用于日志与自检展示）。 */
    static String transportName() {
        return "otlp-http/protobuf".toLowerCase(Locale.ROOT);
    }
}
