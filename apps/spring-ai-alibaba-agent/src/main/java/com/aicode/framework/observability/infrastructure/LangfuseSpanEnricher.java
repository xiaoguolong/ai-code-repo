package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.LangfuseAttributes;
import com.aicode.framework.observability.domain.TraceDimensions;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.Optional;

/**
 * Langfuse trace 维度富化器（Week 18，OTel {@link SpanProcessor}）。
 *
 * <p>Langfuse v4 的过滤与聚合作用在<b>单个 observation</b> 上，官方明确要求把
 * {@code userId} / {@code sessionId} / {@code traceName} / {@code tags} 传播到 trace 内每个 span，
 * 只写 root span 会导致「按用户筛选时看不到子观测」。本处理器在 span 开始时把这些维度写成 span 属性，
 * 覆盖框架自动埋点的 span（HTTP server、Spring AI 的 chat / http client）与我们自己的业务 span。</p>
 *
 * <p>只在 {@code langfuse.enabled=true} 时注册（由 {@code LangfuseConfiguration} 控制），
 * 关闭时零开销、零额外属性。</p>
 *
 * <p>已知取舍：这些 {@code langfuse.*} 属性也会随同一个 {@code BatchSpanProcessor} 发给通用 collector。
 * 如需隔离，在 collector 侧用 {@code attributes} 处理器删除 {@code langfuse.} 前缀即可（见部署文档）。</p>
 */
public class LangfuseSpanEnricher implements SpanProcessor {

    private static final AttributeKey<String> TRACE_NAME = AttributeKey.stringKey(LangfuseAttributes.TRACE_NAME);
    private static final AttributeKey<String> USER_ID = AttributeKey.stringKey(LangfuseAttributes.USER_ID);
    private static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey(LangfuseAttributes.SESSION_ID);
    private static final AttributeKey<java.util.List<String>> TRACE_TAGS =
            AttributeKey.stringArrayKey(LangfuseAttributes.TRACE_TAGS);
    private static final AttributeKey<String> ENVIRONMENT = AttributeKey.stringKey(LangfuseAttributes.ENVIRONMENT);
    private static final AttributeKey<String> RELEASE = AttributeKey.stringKey(LangfuseAttributes.RELEASE);

    private final LangfuseContext langfuseContext;
    private final LangfuseProperties properties;

    public LangfuseSpanEnricher(LangfuseContext langfuseContext, LangfuseProperties properties) {
        this.langfuseContext = langfuseContext;
        this.properties = properties;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        span.setAttribute(ENVIRONMENT, properties.resolvedEnvironment());
        String release = properties.resolvedRelease();
        if (!release.isEmpty()) {
            span.setAttribute(RELEASE, release);
        }
        Optional<TraceDimensions> dimensions = langfuseContext.current();
        if (dimensions.isEmpty()) {
            return;
        }
        TraceDimensions value = dimensions.get();
        setIfPresent(span, TRACE_NAME, value.traceName());
        setIfPresent(span, USER_ID, value.userId());
        setIfPresent(span, SESSION_ID, value.sessionId());
        if (!value.tags().isEmpty()) {
            span.setAttribute(TRACE_TAGS, value.tags());
        }
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // 维度只在开始时写一次；结束时不需要处理
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }

    private void setIfPresent(ReadWriteSpan span, AttributeKey<String> key, String value) {
        if (value != null && !value.isBlank()) {
            span.setAttribute(key, value);
        }
    }
}
