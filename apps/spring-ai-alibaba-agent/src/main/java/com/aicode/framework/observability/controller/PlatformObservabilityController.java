package com.aicode.framework.observability.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.observability.application.PlatformObservabilityUseCase;
import com.aicode.framework.observability.dto.TraceContextResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台可观测自检 API（Week 17）。
 *
 * <p>{@code GET /api/v1/platform/observability/trace-context}：需登录；
 * 返回当前请求的 {@code traceId} / {@code otelTraceId} / {@code spanId} / {@code traceparent}，
 * 用于核对「日志与审计里的 traceId」是否与「链路后端的 traceId」指向同一次请求。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/observability")
public class PlatformObservabilityController {

    private final PlatformObservabilityUseCase platformObservabilityUseCase;

    public PlatformObservabilityController(PlatformObservabilityUseCase platformObservabilityUseCase) {
        this.platformObservabilityUseCase = platformObservabilityUseCase;
    }

    /**
     * 读取当前请求的链路上下文。
     *
     * @return 统一信封包装的链路上下文；未登录抛未授权异常（401）
     */
    @GetMapping("/trace-context")
    public ApiResponse<TraceContextResponse> traceContext() {
        StpUtil.checkLogin();
        return ApiResponse.success(TraceContextResponse.from(platformObservabilityUseCase.currentTraceContext()));
    }
}
