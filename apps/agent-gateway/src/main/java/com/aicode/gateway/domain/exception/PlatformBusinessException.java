package com.aicode.gateway.domain.exception;

import com.aicode.gateway.dto.GatewayErrorCode;

/**
 * 上游（平台）返回了业务失败（4xx，携带自身业务码）。
 *
 * <p>与 {@link GatewayUpstreamException} 的区别：本异常表达的是「平台明确拒绝了这次请求」
 * （未登录 / 无权限 / 参数不合法 / 资源不存在），这些码与状态码可以安全透传给客户端，
 * 便于调用方沿用平台契约；而 {@link GatewayUpstreamException} 表达的是「网关没能完成调用」
 * （不可达 / 超时 / 5xx），对客户端统一收敛为 502。</p>
 *
 * <p><b>文案不外露</b>：上游 message 可能包含内部细节，本异常只在服务端日志里保留，
 * 对外返回本网关自己的安全文案。</p>
 */
public class PlatformBusinessException extends GatewayException {

    private static final long serialVersionUID = 1L;

    private final String upstreamCode;
    private final int upstreamStatus;

    /**
     * @param upstreamCode   上游业务码（如 {@code UNAUTHORIZED}），可为空串
     * @param upstreamStatus 上游 HTTP 状态（4xx）
     * @param message        服务端日志说明（不对外）
     */
    public PlatformBusinessException(String upstreamCode, int upstreamStatus, String message) {
        super(mapCode(upstreamCode, upstreamStatus), message);
        this.upstreamCode = upstreamCode == null ? "" : upstreamCode;
        this.upstreamStatus = upstreamStatus;
    }

    /**
     * 上游业务码。
     *
     * @return 上游 code，缺失时为空串
     */
    public String upstreamCode() {
        return upstreamCode;
    }

    /**
     * 上游 HTTP 状态。
     *
     * @return 状态码
     */
    public int upstreamStatus() {
        return upstreamStatus;
    }

    /**
     * 上游报文里的业务码 → 网关错误码（保证对外 code 一定在网关枚举内）。
     *
     * @param upstreamCode   上游业务码
     * @param upstreamStatus 上游状态
     * @return 网关错误码
     */
    private static GatewayErrorCode mapCode(String upstreamCode, int upstreamStatus) {
        if (upstreamCode != null && !upstreamCode.isBlank()) {
            for (GatewayErrorCode code : GatewayErrorCode.values()) {
                if (code.name().equals(upstreamCode)) {
                    return code;
                }
            }
        }
        return switch (upstreamStatus) {
            case 400 -> GatewayErrorCode.VALIDATION_ERROR;
            case 401 -> GatewayErrorCode.UNAUTHORIZED;
            case 403 -> GatewayErrorCode.FORBIDDEN;
            case 404, 409 -> GatewayErrorCode.GATEWAY_UPSTREAM_ERROR;
            default -> GatewayErrorCode.GATEWAY_UPSTREAM_ERROR;
        };
    }
}
