package com.aicode.framework.dto;

/**
 * 统一响应信封（Week 16 企业规范）。
 *
 * <p>成功与失败结构一致，仅失败时 {@code data} 为 {@code null}。</p>
 *
 * <pre>
 * 成功：{"code":"SUCCESS","message":"OK","data":{...},"traceId":"..."}
 * 失败：{"code":"VALIDATION_ERROR","message":"...","data":null,"traceId":"..."}
 * </pre>
 *
 * <p>该扁平信封自第 2 周起即为全仓实现口径，{@code docs/code-implementation-spec.md} 5.6 已同步校正；
 * 第 16 周相对第 8–15 周的增量只有 {@code traceId} 字段，属兼容扩展。</p>
 *
 * @param code    业务码：成功固定 {@code SUCCESS}，失败取 {@link ApiErrorCode} 枚举名
 * @param message 提示信息，成功后为 {@code OK}，失败为中文说明，不含内部细节
 * @param data    业务数据，失败时为 null
 * @param traceId 链路追踪 ID，与响应头 {@code X-Trace-Id} 一致；无请求上下文时为空串
 * @param <T>     业务数据类型
 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    /** 成功业务码。 */
    public static final String SUCCESS_CODE = "SUCCESS";

    /**
     * 成功响应（无 traceId，供过滤器链之外的内部调用使用）。code=SUCCESS。
     *
     * @param data 业务数据，可为 null
     * @param <T>  业务数据类型
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "OK", data, "");
    }

    /**
     * 失败响应（无 traceId）。data=null。
     *
     * @param code    错误码，应为 {@link ApiErrorCode} 枚举名，保持向后兼容也接受字符串
     * @param message 中文提示
     * @param <T>     业务数据类型
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, "");
    }

    /**
     * 失败响应并附带 traceId。data=null。
     *
     * @param code    错误码，取 {@link ApiErrorCode} 枚举名
     * @param message 中文提示，不得含堆栈或密钥
     * @param traceId 链路追踪 ID，可为空串
     * @param <T>     业务数据类型
     */
    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId == null ? "" : traceId);
    }

    /**
     * 失败响应，错误码与文案取自 {@link ApiErrorCode}。
     *
     * @param errorCode 错误码枚举
     * @param traceId   链路追踪 ID，可为空串
     * @param <T>       业务数据类型
     */
    public static <T> ApiResponse<T> error(ApiErrorCode errorCode, String traceId) {
        return error(errorCode.name(), errorCode.message(), traceId);
    }

    /**
     * 成功响应并附带 traceId。
     *
     * @param data    业务数据，可为 null
     * @param traceId 链路追踪 ID，可为空串
     * @param <T>     业务数据类型
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(SUCCESS_CODE, "OK", data, traceId == null ? "" : traceId);
    }
}
