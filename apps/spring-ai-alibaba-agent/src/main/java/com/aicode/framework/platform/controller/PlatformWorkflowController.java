package com.aicode.framework.platform.controller;

import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformWorkflowUseCase;
import com.aicode.framework.platform.domain.model.PlatformWorkflowDefinition;
import com.aicode.framework.platform.dto.PlatformWorkflowResponse;
import com.aicode.framework.platform.dto.RegisterWorkflowRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台 Workflow 目录 API。
 */
@RestController
@RequestMapping("/api/v1/platform/workflows")
public class PlatformWorkflowController {

    private final PlatformWorkflowUseCase platformWorkflowUseCase;

    public PlatformWorkflowController(PlatformWorkflowUseCase platformWorkflowUseCase) {
        this.platformWorkflowUseCase = platformWorkflowUseCase;
    }

    @GetMapping
    public ApiResponse<List<PlatformWorkflowResponse>> listWorkflows() {
        List<PlatformWorkflowResponse> workflows = platformWorkflowUseCase.listWorkflows().stream()
                .map(PlatformWorkflowResponse::from)
                .toList();
        return ApiResponse.success(workflows);
    }

    @PostMapping
    public ApiResponse<PlatformWorkflowResponse> registerWorkflow(
            @Valid @RequestBody RegisterWorkflowRequest request
    ) {
        PlatformWorkflowDefinition workflow = platformWorkflowUseCase.register(
                request.workflowKey(), request.name(), request.description(), request.boundAgentKey());
        return ApiResponse.success(PlatformWorkflowResponse.from(workflow));
    }

    @GetMapping("/{workflowKey}")
    public ApiResponse<PlatformWorkflowResponse> getWorkflow(@PathVariable String workflowKey) {
        return ApiResponse.success(
                PlatformWorkflowResponse.from(platformWorkflowUseCase.getWorkflow(workflowKey)));
    }
}
