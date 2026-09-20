package com.aicode.patient.controller;

import com.aicode.patient.application.AgentRunUseCase;
import com.aicode.patient.domain.model.AgentResult;
import com.aicode.patient.dto.AgentRunRequest;
import com.aicode.patient.dto.AgentRunResponse;
import com.aicode.patient.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 接入。只做校验与协议转换，任务校验与编排在用例/领域层。
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentRunUseCase agentRunUseCase;

    public AgentController(AgentRunUseCase agentRunUseCase) {
        this.agentRunUseCase = agentRunUseCase;
    }

    /**
     * 提交任务并同步执行 ReAct 循环，携带记忆上下文。
     */
    @PostMapping("/runs")
    public ApiResponse<AgentRunResponse> run(@Valid @RequestBody AgentRunRequest request) {
        AgentResult result = agentRunUseCase.run(request.sessionId(), request.task());
        return ApiResponse.success(AgentRunResponse.from(result));
    }
}
