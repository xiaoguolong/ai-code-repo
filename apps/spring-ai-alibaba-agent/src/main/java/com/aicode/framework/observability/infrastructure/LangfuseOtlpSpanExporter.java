package com.aicode.framework.observability.infrastructure;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Collection;

/**
 * Langfuse OTLP 导出器（Week 18）。
 *
 * <p><b>为什么要包一层</b>：Spring Boot 3.4.5 的 OTLP 自动装配退避条件是
 * {@code @ConditionalOnMissingBean({OtlpGrpcSpanExporter.class, OtlpHttpSpanExporter.class})}，
 * 而 {@code SpanExporters} 收集的是容器里<b>全部</b> {@code SpanExporter} bean。
 * 若直接把 Langfuse 导出器注册成 {@code OtlpHttpSpanExporter} 类型，Boot 的 collector 导出器会整块退避
 * ——既有链路后端会静默收不到数据。包成自定义类型后两个导出器共存，实现真正的「一次埋点、两路导出」。</p>
 *
 * <p>导出失败不抛异常：结果码由 SDK 批量导出线程处理并记日志，业务请求不受影响（与 Week 17 口径一致）。</p>
 */
public class LangfuseOtlpSpanExporter implements SpanExporter {

    /** Langfuse 摄取版本头：不加会让直连 OTel 数据最多延迟 10 分钟可见。 */
    public static final String INGESTION_VERSION_HEADER = "x-langfuse-ingestion-version";

    /** 当前使用的摄取版本（v4 数据模型）。 */
    public static final String INGESTION_VERSION = "4";

    private final SpanExporter delegate;

    /**
     * 按配置创建（内部构建 OTLP/HTTP 导出器并加上 Langfuse 认证头）。
     *
     * @param properties Langfuse 配置，密钥必填
     */
    public LangfuseOtlpSpanExporter(LangfuseProperties properties) {
        this(LangfuseOtlpSpanExporterFactory.create(properties));
    }

    /**
     * 包装已有导出器（便于替换实现与单测委托行为）。
     *
     * @param delegate 实际执行导出的 OTLP 导出器，非 null
     */
    public LangfuseOtlpSpanExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans);
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
