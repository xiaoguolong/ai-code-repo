package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformExecutionUseCase;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.dto.ExecutionRecordResponse;
import com.aicode.framework.platform.dto.PlatformRunRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台执行记录与 Agent 调度 API。
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformExecutionController {

    private final PlatformExecutionUseCase platformExecutionUseCase;

    public PlatformExecutionController(PlatformExecutionUseCase platformExecutionUseCase) {
        this.platformExecutionUseCase = platformExecutionUseCase;
    }

    @GetMapping("/executions")
    public ApiResponse<List<ExecutionRecordResponse>> listExecutions() {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        List<ExecutionRecordResponse> records = platformExecutionUseCase.listExecutions(userId).stream()
                .map(ExecutionRecordResponse::from)
                .toList();
        return ApiResponse.success(records);
    }

    @GetMapping("/executions/{executionId}")
    public ApiResponse<ExecutionRecordResponse> getExecution(@PathVariable String executionId) {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(
                ExecutionRecordResponse.from(platformExecutionUseCase.getExecution(userId, executionId)));
    }

    @PostMapping("/agents/{agentKey}/runs")
    public ApiResponse<ExecutionRecordResponse> runAgent(
            @PathVariable String agentKey,
            @Valid @RequestBody PlatformRunRequest request
    ) {
        StpUtil.checkLogin();
        long userId = StpUtil.getLoginIdAsLong();
        Map<String, Object> input = request.input() == null ? Map.of() : request.input();
        ExecutionRecord record = platformExecutionUseCase.runAgent(userId, agentKey, input);
        return ApiResponse.success(ExecutionRecordResponse.from(record));
    }
}
