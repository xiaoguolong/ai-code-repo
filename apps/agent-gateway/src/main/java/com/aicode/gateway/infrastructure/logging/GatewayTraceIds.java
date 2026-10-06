package com.aicode.gateway.infrastructure.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网关侧链路 ID 工具（Week 19）。
 *
 * <p><b>为什么复刻一份而不是依赖平台类</b>：网关与平台是两个 jar、两条独立发布线，
 * 网关引入平台模块会把整套业务依赖（Spring AI、Flyway、Sa-Token 业务配置）拖进来，
 * 与「网关只做入口」的边界冲突。因此这里只复刻<b>纯函数</b>的 ID 派生规则，
 * 并用 {@code GatewayTraceIdsTest} 锁死与平台 {@code TraceIds} 的<b>逐位一致</b>
 * （同样的输入必须得到同样的 OTel traceId，否则两侧日志与链路会对不上）。</p>
 *
 * <p>规则（与平台 Week 17 相同）：自研 traceId 若本身是合法 W3C hex 则原样使用；
 * 否则取 {@code MD5(traceId)} 的 32 位小写 hex。</p>
 */
public final class GatewayTraceIds {

    /** 业务链路 ID 头名（与平台/响应头一致）。 */
    public static final String HEADER_NAME = GatewayAttributes.TRACE_ID_HEADER;

    /** W3C traceparent 头名。 */
    public static final String TRACEPARENT_HEADER = GatewayAttributes.TRACEPARENT_HEADER;

    /** traceId 长度上限，超长截断（避免污染日志与响应头）。 */
    public static final int MAX_LENGTH = 64;

    /** W3C trace-id 长度。 */
    public static final int OTEL_TRACE_ID_LENGTH = 32;

    /** W3C spanId 长度。 */
    public static final int SPAN_ID_LENGTH = 16;

    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile(
            "^(\\d{2})-([0-9a-fA-F]{" + OTEL_TRACE_ID_LENGTH + "})-([0-9a-fA-F]{" + SPAN_ID_LENGTH + "})-(\\d{2})$");

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{1," + OTEL_TRACE_ID_LENGTH + "}$");

    private GatewayTraceIds() {
    }

    /**
     * 归一化外部传入的链路 ID：去空白、限长。
     *
     * @param raw 原始值，可为 null
     * @return 可安全写入 MDC 与响应头的值；非法时为空串
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
     * 生成新的链路 ID（32 位小写 hex，与平台同规格）。
     *
     * @return 非全 0 的 32 位 hex
     */
    public static String newTraceId() {
        String candidate = UUID.randomUUID().toString().replace("-", "");
        return isAllZero(candidate) ? newTraceId() : candidate;
    }

    /**
     * 生成新的 spanId（16 位小写 hex）。
     *
     * @return 非全 0 的 16 位 hex
     */
    public static String newSpanId() {
        String candidate = UUID.randomUUID().toString().replace("-", "").substring(0, SPAN_ID_LENGTH);
        return isAllZero(candidate) ? newSpanId() : candidate;
    }

    /**
     * 业务链路 ID → W3C（OTel）traceId，确定性换算（与平台 {@code TraceIds.otelTraceId} 一致）。
     *
     * @param traceId 业务链路 ID，可为 null
     * @return 32 位小写 hex；输入为空时返回 32 个 0（调用方应保证非空）
     */
    public static String otelTraceId(String traceId) {
        String normalized = normalize(traceId).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || isAllZero(normalized)) {
            // 空与全 0 都视为「没有可用 traceId」：W3C 禁止全 0 的 trace-id，
            // 若交给 MD5 分支会把全 0 变成一个"看起来合法"的随机 ID，反而掩盖调用方的错误。
            return "0".repeat(OTEL_TRACE_ID_LENGTH);
        }
        if (HEX_PATTERN.matcher(normalized).matches()) {
            return "0".repeat(OTEL_TRACE_ID_LENGTH - normalized.length()) + normalized;
        }
        return md5Hex(normalized);
    }

    /**
     * 解析 W3C traceparent。
     *
     * @param raw 头原文，可为 null
     * @return 合法时返回 {@code [traceId, spanId, sampled]}；非法返回 null
     */
    public static String[] parseTraceparent(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = TRACEPARENT_PATTERN.matcher(raw.trim());
        if (!matcher.matches() || !"00".equals(matcher.group(1))) {
            return null;
        }
        String traceId = matcher.group(2).toLowerCase(Locale.ROOT);
        String spanId = matcher.group(3).toLowerCase(Locale.ROOT);
        if (isAllZero(traceId) || isAllZero(spanId)) {
            return null;
        }
        return new String[] {traceId, spanId, matcher.group(4)};
    }

    /**
     * 格式化 W3C traceparent。
     *
     * @param traceId 32 位 hex
     * @param spanId  16 位 hex
     * @param sampled 是否采样
     * @return {@code 00-<traceId>-<spanId>-01|00}；参数非法时为空串
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

    /** 是否全 0（W3C 禁止全 0 的 trace-id / parent-id）。 */
    private static boolean isAllZero(String hex) {
        return hex.chars().allMatch(ch -> ch == '0');
    }

    /** MD5 摘要的 32 位小写 hex。 */
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
