package com.aicode.framework.observability.domain;

/**
 * 一次健康探测的结果（Week 20）。
 *
 * <p>只保留判断所需的最小信息：是否可达、HTTP 状态码、失败原因摘要。
 * <b>不保留响应正文</b>——后端返回的正文可能含内部信息，自检接口不应转发它。</p>
 *
 * @param reachable  是否可达（2xx / 3xx 视为可达）
 * @param statusCode HTTP 状态码；探测未建立连接时为 0
 * @param detail     一行摘要（形如 {@code HTTP 200} 或 {@code ConnectException}）
 */
public record MonitoringProbeResult(boolean reachable, int statusCode, String detail) {

    /** 摘要最大长度：防止异常信息过长污染响应。 */
    private static final int MAX_DETAIL_CHARS = 120;

    /** 空值截断。 */
    public MonitoringProbeResult {
        detail = detail == null ? "" : detail.strip();
        if (detail.length() > MAX_DETAIL_CHARS) {
            detail = detail.substring(0, MAX_DETAIL_CHARS);
        }
    }

    /**
     * 可达结果。
     *
     * @param statusCode HTTP 状态码
     * @return 结果
     */
    public static MonitoringProbeResult up(int statusCode) {
        return new MonitoringProbeResult(true, statusCode, "HTTP " + statusCode);
    }

    /**
     * 不可达结果。
     *
     * @param detail 失败摘要（异常类型短名 / 超时说明）
     * @return 结果
     */
    public static MonitoringProbeResult down(String detail) {
        return new MonitoringProbeResult(false, 0, detail);
    }
}
