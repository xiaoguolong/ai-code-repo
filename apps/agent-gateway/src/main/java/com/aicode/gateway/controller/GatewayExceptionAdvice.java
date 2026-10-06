package com.aicode.gateway.controller;

import com.aicode.gateway.domain.exception.GatewayException;
import com.aicode.gateway.domain.exception.GatewayUpstreamException;
import com.aicode.gateway.domain.exception.PlatformBusinessException;
import com.aicode.gateway.dto.ApiResponse;
import com.aicode.gateway.dto.GatewayErrorCode;
import com.aicode.gateway.infrastructure.logging.GatewayAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

/**
 * 网关统一异常处理（Week 19）。
 *
 * <p>对外只给「错误码 + 中文安全文案 + traceId」；堆栈、SQL、上游原始报文一律只进服务端日志
 * （规范 5.7）。状态码由错误码决定，禁止一律 200（规范 5.6）。</p>
 */
@RestControllerAdvice
public class GatewayExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionAdvice.class);

    /**
     * 网关领域异常（含未授权、上游错误、上游业务失败）。
     *
     * @param exception 领域异常
     * @param exchange  交换对象（读取 traceId）
     * @return 统一信封 + 语义化状态码
     */
    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handleGatewayException(
            GatewayException exception, ServerWebExchange exchange) {
        String traceId = traceId(exchange);
        GatewayErrorCode code = exception.errorCode();
        if (exception instanceof PlatformBusinessException business) {
            // 上游 4xx：透传上游业务码与状态（平台契约的一部分），但不透传上游文案
            GatewayErrorCode upstreamCode = GatewayErrorCode.fromNameOrUpstream(business.upstreamCode());
            log.info("[gateway] 上游业务失败 code={} status={} traceId={}",
                    business.upstreamCode(), business.upstreamStatus(), traceId);
            return ResponseEntity.status(business.upstreamStatus() > 0
                            ? HttpStatus.valueOf(business.upstreamStatus())
                            : upstreamCode.status())
                    .body(ApiResponse.error(upstreamCode, traceId));
        }
        if (exception instanceof GatewayUpstreamException upstream && upstream.upstreamStatus() > 0) {
            log.warn("[gateway] 上游异常 code={} status={} traceId={}",
                    upstream.upstreamCode(), upstream.upstreamStatus(), traceId);
        } else {
            // 连接层/编码层失败（Feign status=-1）在日志里必须能看出原因，否则只剩「上游不可用」无从排查
            log.warn("[gateway] 请求失败 code={} traceId={} message={} cause={}",
                    code, traceId, exception.getMessage(),
                    exception.getCause() == null ? "-" : exception.getCause().toString(),
                    exception);
        }
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, traceId));
    }

    /**
     * Bean Validation 失败（请求体校验）。
     *
     * @param exception 校验异常
     * @param exchange  交换对象
     * @return 400 统一信封
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            WebExchangeBindException exception, ServerWebExchange exchange) {
        String detail = exception.getFieldErrors().isEmpty()
                ? "请求参数不合法"
                : exception.getFieldErrors().get(0).getField() + " " + exception.getFieldErrors().get(0).getDefaultMessage();
        log.info("[gateway] 参数校验失败：{}", detail);
        return ResponseEntity.status(GatewayErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.error(GatewayErrorCode.VALIDATION_ERROR, detail, traceId(exchange)));
    }

    /**
     * 框架抛出的状态异常（例如 404 路由不存在）。
     *
     * @param exception 状态异常
     * @param exchange  交换对象
     * @return 保留原状态码的统一信封
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleStatus(
            ResponseStatusException exception, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        GatewayErrorCode code = switch (resolved) {
            case NOT_FOUND -> GatewayErrorCode.VALIDATION_ERROR;
            case UNAUTHORIZED -> GatewayErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> GatewayErrorCode.FORBIDDEN;
            default -> resolved.is4xxClientError() ? GatewayErrorCode.VALIDATION_ERROR : GatewayErrorCode.INTERNAL_ERROR;
        };
        return ResponseEntity.status(resolved).body(ApiResponse.error(code, traceId(exchange)));
    }

    /**
     * 兜底：未预期异常。
     *
     * @param exception 异常
     * @param exchange  交换对象
     * @return 500 统一信封（不含堆栈）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, ServerWebExchange exchange) {
        String traceId = traceId(exchange);
        log.error("[gateway] 未预期异常 traceId={}", traceId, exception);
        return ResponseEntity.status(GatewayErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.error(GatewayErrorCode.INTERNAL_ERROR, traceId));
    }

    /** 从请求属性取链路 ID。 */
    private static String traceId(ServerWebExchange exchange) {
        Object value = exchange.getAttributes().get(GatewayAttributes.ATTR_TRACE_ID);
        return value == null ? "" : value.toString();
    }
}
