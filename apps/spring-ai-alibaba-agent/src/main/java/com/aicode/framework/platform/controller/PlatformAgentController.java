package com.aicode.framework.platform.controller;

import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.platform.application.PlatformAgentUseCase;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.dto.PlatformAgentResponse;
import com.aicode.framework.platform.dto.RegisterAgentRequest;
import com.aicode.framework.platform.dto.UpdateAgentConfigRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台 Agent 注册与配置 API。
 */
@RestController
@RequestMapping("/api/v1/platform/agents")
public class PlatformAgentController {

    private final PlatformAgentUseCase platformAgentUseCase;

    public PlatformAgentController(PlatformAgentUseCase platformAgentUseCase) {
        this.platformAgentUseCase = platformAgentUseCase;
    }

    @GetMapping
    public ApiResponse<List<PlatformAgentResponse>> listAgents() {
        List<PlatformAgentResponse> agents = platformAgentUseCase.listAgents().stream()
                .map(PlatformAgentResponse::from)
                .toList();
        return ApiResponse.success(agents);
    }

    @PostMapping
    public ApiResponse<PlatformAgentResponse> registerAgent(@Valid @RequestBody RegisterAgentRequest request) {
        PlatformAgentDefinition agent = platformAgentUseCase.register(
                request.agentKey(), request.name(), request.description(), request.agentType());
        return ApiResponse.success(PlatformAgentResponse.from(agent));
    }

    @GetMapping("/{agentKey}")
    public ApiResponse<PlatformAgentResponse> getAgent(@PathVariable String agentKey) {
        return ApiResponse.success(PlatformAgentResponse.from(platformAgentUseCase.getAgent(agentKey)));
    }

    @PutMapping("/{agentKey}/config")
    public ApiResponse<PlatformAgentResponse> updateConfig(
            @PathVariable String agentKey,
            @Valid @RequestBody UpdateAgentConfigRequest request
    ) {
        PlatformAgentConfig config = new PlatformAgentConfig(
                request.enabled(), request.maxIterations(), request.temperature(), request.model());
        PlatformAgentDefinition agent = platformAgentUseCase.updateConfig(agentKey, config);
        return ApiResponse.success(PlatformAgentResponse.from(agent));
    }
}
