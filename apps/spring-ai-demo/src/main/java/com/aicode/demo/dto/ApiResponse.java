package com.aicode.demo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * HTTP 响应信封。成功只带 data，失败只带 error。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, ErrorBody error) {

    /**
     * 成功响应。
     */
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    /**
     * 失败响应。
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, new ErrorBody(code, message));
    }
}
