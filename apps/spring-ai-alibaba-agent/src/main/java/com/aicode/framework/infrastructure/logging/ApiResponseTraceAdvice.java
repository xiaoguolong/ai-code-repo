package com.aicode.framework.infrastructure.logging;

import com.aicode.framework.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 响应信封追踪 ID 填充（Week 16 异常规范）。
 *
 * <p>统一给所有 {@link ApiResponse} 补 {@code traceId}，使成功与失败响应口径一致，
 * 调用方可凭 traceId 直接串联服务端日志。Controller 无需各自处理。</p>
 */
@RestControllerAdvice
public class ApiResponseTraceAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ApiResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse<?> apiResponse) {
            String traceId = TraceIds.current();
            if (apiResponse.traceId() == null || apiResponse.traceId().isEmpty()) {
                return new ApiResponse<>(
                        apiResponse.code(), apiResponse.message(), apiResponse.data(), traceId);
            }
        }
        return body;
    }
}
