package com.aicode.framework.dto;

/**
 * 统一响应信封。成功与失败都是相同的 {@code code / message / data} 结构，仅失败时 {@code data} 为 null。
 *
 * @param code    业务码：成功为 {@code SUCCESS}，失败为具体错误码
 * @param message 提示信息
 * @param data    业务数据（泛型）
 */
public record ApiResponse<T>(String code, String message, T data) {

    /**
     * 成功响应。code=SUCCESS。
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "OK", data);
    }

    /**
     * 失败响应。data=null。
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
