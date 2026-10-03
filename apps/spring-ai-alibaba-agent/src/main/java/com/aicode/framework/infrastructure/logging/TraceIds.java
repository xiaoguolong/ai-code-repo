package com.aicode.framework.infrastructure.logging;

import org.slf4j.MDC;

/**
 * 链路追踪 ID 工具（Week 16 日志规范）。
 *
 * <p>约定：请求进入时由 {@link TraceIdFilter} 写入 MDC 键 {@link #MDC_KEY}，
 * 业务与异常处理统一从这里读取，避免各处硬编码键名。</p>
 */
public final class TraceIds {

    /** MDC 中的 traceId 键名，同时是响应头名（{@code X-Trace-Id}）。 */
    public static final String MDC_KEY = "traceId";

    /** HTTP 请求 / 响应头中的链路追踪标识。 */
    public static final String HEADER_NAME = "X-Trace-Id";

    /** traceId 长度上限，超长截断，避免日志与响应头被污染。 */
    public static final int MAX_LENGTH = 64;

    private TraceIds() {
    }

    /**
     * 读取当前请求上下文的 traceId。
     *
     * <p>无请求上下文（定时任务、纯单测）时返回空串而非 null，调用方无需判空。</p>
     *
     * @return traceId，缺失时为空串
     */
    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "" : traceId;
    }

    /**
     * 归一化外部传入的 traceId：去空白、限长，非法则返回空串（由调用方改用新生成值）。
     *
     * @param raw 请求头中的原始值，可为 null
     * @return 可安全写入 MDC 与响应头的值，非空
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }
}
