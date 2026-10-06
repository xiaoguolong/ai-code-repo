package com.aicode.gateway.domain.exception;

import com.aicode.gateway.dto.GatewayErrorCode;

/**
 * 上游平台服务调用失败（网络 / 超时 / 非法报文 / 5xx，HTTP 502）。
 *
 * <p>若上游返回的是 4xx 且带自身业务码，调用方应改用
 * {@link #GatewayUpstreamException(com.aicode.gateway.dto.GatewayErrorCode, String, String, int)}
 * 构造，把平台业务码与安全文案透传给客户端。</p>
 */
public class GatewayUpstreamException extends GatewayException {

    private static final long serialVersionUID = 1L;

    private final String upstreamCode;
    private final int upstreamStatus;

    /**
     * 上游不可达 / 报文异常 / 5xx。
     *
     * @param message 服务端日志说明
     * @param cause   原因，可为 null
     */
    public GatewayUpstreamException(String message, Throwable cause) {
        super(GatewayErrorCode.GATEWAY_UPSTREAM_ERROR, message, cause);
        this.upstreamCode = "";
        this.upstreamStatus = 0;
    }

    /**
     * 透传上游业务码（仅限 4xx 且文案安全的场景）。
     *
     * @param errorCode      对外的错误码
     * @param message        服务端日志说明
     * @param upstreamCode   上游业务码（如 {@code UNAUTHORIZED}），可为空串
     * @param upstreamStatus 上游 HTTP 状态
     */
    public GatewayUpstreamException(
            GatewayErrorCode errorCode, String message, String upstreamCode, int upstreamStatus) {
        super(errorCode, message);
        this.upstreamCode = upstreamCode == null ? "" : upstreamCode;
        this.upstreamStatus = upstreamStatus;
    }

    /**
     * 上游业务码（原始，仅用于日志与响应体 code 透传）。
     *
     * @return 上游 code，缺失时为空串
     */
    public String upstreamCode() {
        return upstreamCode;
    }

    /**
     * 上游 HTTP 状态；非透传场景为 0。
     *
     * @return 上游状态码
     */
    public int upstreamStatus() {
        return upstreamStatus;
    }
}
