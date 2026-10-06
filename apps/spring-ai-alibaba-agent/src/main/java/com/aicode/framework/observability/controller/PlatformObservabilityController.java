package com.aicode.framework.observability.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.observability.application.PlatformLlmCostUseCase;
import com.aicode.framework.observability.application.PlatformObservabilityUseCase;
import com.aicode.framework.observability.dto.LangfuseStatusResponse;
import com.aicode.framework.observability.dto.LlmCostSummaryResponse;
import com.aicode.framework.observability.dto.SkyWalkingStatusResponse;
import com.aicode.framework.observability.dto.TraceContextResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台可观测自检 API（Week 17 新增，Week 18/19 扩展）。
 *
 * <ul>
 *   <li>{@code GET /observability/trace-context}：链路上下文（Week 17），Week 18 增加 Langfuse 深链字段；</li>
 *   <li>{@code GET /observability/langfuse-status}：Langfuse 接入自检（不回显密钥）；</li>
 *   <li>{@code GET /observability/skywalking-status}：SkyWalking 接入自检（Week 19，不回显密钥）；</li>
 *   <li>{@code GET /observability/llm-cost-summary}：按模型的 Token 与成本汇总（仅管理员）。</li>
 * </ul>
 *
 * <p>Controller 只做登录态校验与协议转换，权限规则在用例层（规范 5.2）。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/observability")
public class PlatformObservabilityController {

    private final PlatformObservabilityUseCase platformObservabilityUseCase;
    private final PlatformLlmCostUseCase platformLlmCostUseCase;

    public PlatformObservabilityController(
            PlatformObservabilityUseCase platformObservabilityUseCase,
            PlatformLlmCostUseCase platformLlmCostUseCase
    ) {
        this.platformObservabilityUseCase = platformObservabilityUseCase;
        this.platformLlmCostUseCase = platformLlmCostUseCase;
    }

    /**
     * 读取当前请求的链路上下文。
     *
     * @return 统一信封包装的链路上下文；未登录抛未授权异常（401）
     */
    @GetMapping("/trace-context")
    public ApiResponse<TraceContextResponse> traceContext() {
        StpUtil.checkLogin();
        return ApiResponse.success(TraceContextResponse.from(
                platformObservabilityUseCase.currentTraceContext(),
                platformObservabilityUseCase.langfuseTraceUrl()));
    }

    /**
     * Langfuse 接入自检。
     *
     * @return 开关、端点、正文采集有效值、Prompt 管理状态与本次请求的 trace 维度（不含 secret key）
     */
    @GetMapping("/langfuse-status")
    public ApiResponse<LangfuseStatusResponse> langfuseStatus() {
        StpUtil.checkLogin();
        return ApiResponse.success(LangfuseStatusResponse.from(platformObservabilityUseCase.langfuseStatus()));
    }

    /**
     * LLM 成本汇总（管理员）。
     *
     * @return 按模型聚合的 Token 与估算成本；非管理员 403
     */
    @GetMapping("/llm-cost-summary")
    public ApiResponse<LlmCostSummaryResponse> llmCostSummary() {
        StpUtil.checkLogin();
        return ApiResponse.success(LlmCostSummaryResponse.from(
                platformLlmCostUseCase.summarize(StpUtil.getLoginIdAsLong())));
    }

    /**
     * SkyWalking 接入自检（Week 19）。
     *
     * @return 开关、Agent 是否挂载、OAP 地址与本次请求的两套链路 ID（不含密钥）
     */
    @GetMapping("/skywalking-status")
    public ApiResponse<SkyWalkingStatusResponse> skywalkingStatus() {
        StpUtil.checkLogin();
        return ApiResponse.success(SkyWalkingStatusResponse.from(
                platformObservabilityUseCase.skywalkingStatus()));
    }
}