package com.aicode.framework.controller;

import com.aicode.framework.application.FrameworkAgentUseCase;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.dto.FrameworkAgentRunRequest;
import com.aicode.framework.dto.FrameworkAgentRunResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring AI Alibaba Graph Agent 接入。只做校验与协议转换，编排在应用层与领域层。
 */
@RestController
@RequestMapping("/api/v1/framework/agents")
public class FrameworkAgentController {

    private final FrameworkAgentUseCase frameworkAgentUseCase;

    public FrameworkAgentController(FrameworkAgentUseCase frameworkAgentUseCase) {
        this.frameworkAgentUseCase = frameworkAgentUseCase;
    }

    /**
     * 提交任务并同步执行 Graph Agent，返回最终答案与工具轨迹。
     */
    @PostMapping("/runs")
    public ApiResponse<FrameworkAgentRunResponse> run(@Valid @RequestBody FrameworkAgentRunRequest request) {
        FrameworkAgentResult result = frameworkAgentUseCase.run(request.task());
        return ApiResponse.success(FrameworkAgentRunResponse.from(result));
    }
}
