package com.aicode.gateway.dto;

/**
 * 统一响应信封（Week 16 规范，网关侧同形状复刻）。
 *
 * <p>网关与平台是两个进程、两个 jar，无法共享类；但对外契约必须一致，故这里保持
 * <b>与平台 {@code com.aicode.framework.dto.ApiResponse} 逐字段相同</b>的形状
 * （{@code code} / {@code message} / {@code data} / {@code traceId}），
 * 由契约测试（{@code GatewayEnvelopeContractTest}）锁死字段名与语义。</p>
 *
 * @param code    业务码：成功固定 {@code SUCCESS}，失败取 {@link GatewayErrorCode} 枚举名
 * @param message 提示信息：成功为 {@code OK}，失败为中文说明（不含堆栈 / 上游报文 / 密钥）
 * @param data    业务数据，失败时恒为 {@code null}
 * @param traceId 链路追踪 ID，与响应头 {@code X-Trace-Id} 一致
 * @param <T>     业务数据类型
 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    /** 成功业务码。 */
    public static final String SUCCESS_CODE = "SUCCESS";

    /**
     * 成功响应。
     *
     * @param data    业务数据，可为 null
     * @param traceId 链路 ID，可为空串
     * @param <T>     业务数据类型
     * @return 成功信封
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(SUCCESS_CODE, "OK", data, traceId == null ? "" : traceId);
    }

    /**
     * 失败响应。
     *
     * @param errorCode 错误码枚举
     * @param traceId   链路 ID，可为空串
     * @param <T>       业务数据类型
     * @return 失败信封（{@code data} 为 null）
     */
    public static <T> ApiResponse<T> error(GatewayErrorCode errorCode, String traceId) {
        return new ApiResponse<>(errorCode.name(), errorCode.message(), null, traceId == null ? "" : traceId);
    }

    /**
     * 失败响应（自定义中文文案，用于把上游可安全外露的提示透传）。
     *
     * @param errorCode 错误码枚举
     * @param message   中文提示，不得含堆栈 / SQL / 上游原始报文 / 密钥
     * @param traceId   链路 ID，可为空串
     * @param <T>       业务数据类型
     * @return 失败信封
     */
    public static <T> ApiResponse<T> error(GatewayErrorCode errorCode, String message, String traceId) {
        return new ApiResponse<>(errorCode.name(), message, null, traceId == null ? "" : traceId);
    }
}
