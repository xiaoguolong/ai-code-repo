package com.aicode.gateway.dto;

import org.springframework.http.HttpStatus;

/**
 * 网关错误码唯一来源（Week 16 规范在网关侧的落地）。
 *
 * <p>约定与平台一致：</p>
 * <ul>
 *   <li>枚举名即对外 {@code code}（{@code UPPER_SNAKE_CASE}），禁止临时字符串；</li>
 *   <li>成功码固定 {@code SUCCESS}，不在此枚举；</li>
 *   <li>{@link #message()} 是兜底中文文案，不含堆栈、SQL、上游原始报文、密钥；</li>
 *   <li>{@link #status()} 决定 HTTP 状态码，禁止一律 200。</li>
 * </ul>
 *
 * <p>上游（平台）返回的失败会映射为 {@link #GATEWAY_UPSTREAM_ERROR}，但上游 4xx 的
 * <b>错误码与安全文案</b>可原样透传（见 {@code GatewayAgentUseCase} 的映射规则），
 * 以便调用方仍能按平台契约处理。</p>
 */
public enum GatewayErrorCode {

    /** 入参校验失败。HTTP 400。 */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数不合法"),

    /** 未携带会话或会话已过期。HTTP 401。 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),

    /** 会话有效但无权访问该资源。HTTP 403。 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权访问该资源"),

    /** 上游（平台服务）不可达、超时或返回非法报文。HTTP 502。 */
    GATEWAY_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "上游服务暂时不可用，请稍后重试"),

    /** 上游返回 5xx。HTTP 502（对调用方而言是网关侧失败）。 */
    UPSTREAM_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "上游服务处理失败，请稍后重试"),

    /** 未预期异常，对外只给通用文案，堆栈仅进服务端日志。HTTP 500。 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");

    private final HttpStatus status;
    private final String message;

    GatewayErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 该错误码对应的 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 兜底中文文案。
     *
     * @return 中文提示
     */
    public String message() {
        return message;
    }

    /**
     * 按名称解析错误码（用于透传上游业务码）。
     *
     * @param name 枚举名，可为 null
     * @return 命中时返回对应枚举；未命中返回 {@link #GATEWAY_UPSTREAM_ERROR}
     */
    public static GatewayErrorCode fromNameOrUpstream(String name) {
        if (name == null || name.isBlank()) {
            return GATEWAY_UPSTREAM_ERROR;
        }
        for (GatewayErrorCode code : values()) {
            if (code.name().equals(name)) {
                return code;
            }
        }
        return GATEWAY_UPSTREAM_ERROR;
    }
}
