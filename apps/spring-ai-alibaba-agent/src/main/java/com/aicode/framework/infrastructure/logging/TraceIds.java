package com.aicode.framework.infrastructure.logging;

import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 链路追踪 ID 工具（Week 16 日志规范 + Week 17 OpenTelemetry 对齐）。
 *
 * <p>约定：请求进入时由 {@link TraceIdFilter} 写入 MDC 键 {@link #MDC_KEY}，
 * 业务与异常处理统一从这里读取，避免各处硬编码键名。</p>
 *
 * <p><b>Week 17 单源派生</b>：OTel 的 traceId 是 32 位小写 hex，而自研 {@code X-Trace-Id}
 * 允许任意字符串。若两套 ID 各生成一套，"日志里的 ID" 与 "链路后端里的 ID" 就对不上。
 * 因此这里定义<b>确定性换算</b>：</p>
 * <ol>
 *   <li>自研 traceId 已是合法 W3C hex（1–32 位且非全 0）→ 直接作为 OTel traceId；</li>
 *   <li>否则 → {@code MD5(traceId)} 取 32 位 hex，同输入恒等。</li>
 * </ol>
 * <p>由第 2 条可知：MD5 的输入差异必然导致输出差异，因此不同 traceId 不会共用同一条链路。</p>
 */
public final class TraceIds {

    /** MDC 中的 traceId 键名，同时是响应头名（{@code X-Trace-Id}）。 */
    public static final String MDC_KEY = "traceId";

    /**
     * MDC 中的上游父 spanId 键名（Week 17）。
     *
     * <p>仅当请求带合法 {@code traceparent} 时写入：OTel 规范要求「远程父」必须带上游 spanId，
     * 否则链路后端无法把本服务与上游正确连边。写入 span 属性与 MDC，供适配器构造父上下文。</p>
     */
    public static final String MDC_PARENT_SPAN_ID = "traceParentSpanId";

    /** HTTP 请求 / 响应头中的链路追踪标识。 */
    public static final String HEADER_NAME = "X-Trace-Id";

    /** W3C Trace Context 标准头名。 */
    public static final String TRACEPARENT_HEADER = "traceparent";

    /** traceId 长度上限，超长截断，避免日志与响应头被污染。 */
    public static final int MAX_LENGTH = 64;

    /** W3C trace-id 长度（32 位小写 hex）。 */
    public static final int OTEL_TRACE_ID_LENGTH = 32;

    /** W3C parent-id / spanId 长度（16 位小写 hex）。 */
    public static final int SPAN_ID_LENGTH = 16;

    /** W3C traceparent：{@code 00-<32hex>-<16hex>-<2hex>}。 */
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile(
            "^(\\d{2})-([0-9a-fA-F]{" + OTEL_TRACE_ID_LENGTH + "})-([0-9a-fA-F]{" + SPAN_ID_LENGTH + "})-(\\d{2})$");

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{1," + OTEL_TRACE_ID_LENGTH + "}$");

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

    /**
     * 生成新的自研 traceId。长度取 32 位 hex（W3C trace-id 标准长度），
     * 这样无上游输入时 OTel traceId 可以原样复用，不产生任何换算开销，也无需补零。
     *
     * @return 32 位小写 hex，非全 0
     */
    public static String newTraceId() {
        return randomHex(OTEL_TRACE_ID_LENGTH);
    }

    /**
     * 生成新的 spanId（16 位小写 hex）。
     *
     * @return 16 位小写 hex，非全 0
     */
    public static String newSpanId() {
        return randomHex(SPAN_ID_LENGTH);
    }

    /** 生成指定位数的小写 hex 随机串，全 0 时重试（W3C 禁止全 0 的 trace-id / parent-id）。 */
    private static String randomHex(int length) {
        String candidate = UUID.randomUUID().toString().replace("-", "").substring(0, length);
        return isAllZero(candidate) ? randomHex(length) : candidate;
    }

    /**
     * 把自研 traceId 确定性换算为 OTel（W3C）traceId。
     *
     * @param traceId 自研 traceId，可为 null / 空白
     * @return 32 位小写 hex；输入为空时返回 32 个 0（调用方应保证非空）
     */
    public static String otelTraceId(String traceId) {
        String normalized = normalize(traceId).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "0".repeat(OTEL_TRACE_ID_LENGTH);
        }
        if (HEX_PATTERN.matcher(normalized).matches() && !isAllZero(normalized)) {
            return leftPadHex(normalized);
        }
        return md5Hex(normalized);
    }

    /**
     * 解析 W3C {@code traceparent} 头。
     *
     * @param raw 头原文，可为 null
     * @return 合法时返回 {@link TraceContext#valid()} 为 true 的结果；否则 {@link TraceContext#invalid()}
     */
    public static TraceContext parseTraceparent(String raw) {
        if (raw == null) {
            return TraceContext.invalid();
        }
        Matcher matcher = TRACEPARENT_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return TraceContext.invalid();
        }
        if (!"00".equals(matcher.group(1))) {
            return TraceContext.invalid();
        }
        String traceId = matcher.group(2).toLowerCase(Locale.ROOT);
        String parentSpanId = matcher.group(3).toLowerCase(Locale.ROOT);
        if (isAllZero(traceId) || isAllZero(parentSpanId)) {
            return TraceContext.invalid();
        }
        int flags = Integer.parseInt(matcher.group(4), 16);
        return new TraceContext(true, traceId, parentSpanId, (flags & 0x01) == 0x01);
    }

    /**
     * 格式化 W3C {@code traceparent} 头（版本固定 00）。
     *
     * @param traceId      32 位 hex traceId
     * @param spanId       16 位 hex spanId
     * @param sampled      是否采样
     * @return {@code 00-<traceId>-<spanId>-01|00}；当 traceId 或 spanId 为空时返回空串（不产生非法头）
     */
    public static String formatTraceparent(String traceId, String spanId, boolean sampled) {
        String trace = normalize(traceId);
        String span = normalize(spanId);
        if (trace.isEmpty() || span.isEmpty() || isAllZero(trace) || isAllZero(span)) {
            return "";
        }
        return "00-" + trace.toLowerCase(Locale.ROOT) + "-" + span.toLowerCase(Locale.ROOT)
                + "-" + (sampled ? "01" : "00");
    }

    /** 判断是否全 0（W3C 明确禁止全 0 的 trace-id 与 parent-id）。 */
    private static boolean isAllZero(String hex) {
        return hex.chars().allMatch(ch -> ch == '0');
    }

    /** 短 hex 左补 0 到 32 位：W3C 允许 trace-id 前导 0 省略。 */
    private static String leftPadHex(String hex) {
        return "0".repeat(OTEL_TRACE_ID_LENGTH - hex.length()) + hex;
    }

    /** MD5 摘要的 32 位小写 hex 表示。 */
    private static String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 not available", ex);
        }
    }
}
