package com.aicode.framework.platform.controller;

import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformToolUseCase;
import com.aicode.framework.platform.domain.model.PlatformToolDefinition;
import com.aicode.framework.platform.dto.PlatformToolResponse;
import com.aicode.framework.platform.dto.RegisterToolRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台 Tool 目录 API。
 */
@RestController
@RequestMapping("/api/v1/platform/tools")
public class PlatformToolController {

    private final PlatformToolUseCase platformToolUseCase;

    public PlatformToolController(PlatformToolUseCase platformToolUseCase) {
        this.platformToolUseCase = platformToolUseCase;
    }

    @GetMapping
    public ApiResponse<List<PlatformToolResponse>> listTools() {
        List<PlatformToolResponse> tools = platformToolUseCase.listTools().stream()
                .map(PlatformToolResponse::from)
                .toList();
        return ApiResponse.success(tools);
    }

    @PostMapping
    public ApiResponse<PlatformToolResponse> registerTool(@Valid @RequestBody RegisterToolRequest request) {
        PlatformToolDefinition tool = platformToolUseCase.register(
                request.toolKey(), request.toolName(), request.description());
        return ApiResponse.success(PlatformToolResponse.from(tool));
    }

    @GetMapping("/{toolKey}")
    public ApiResponse<PlatformToolResponse> getTool(@PathVariable String toolKey) {
        return ApiResponse.success(PlatformToolResponse.from(platformToolUseCase.getTool(toolKey)));
    }
}
