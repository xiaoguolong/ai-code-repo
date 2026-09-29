package com.aicode.framework.multiagent.controller;

import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.multiagent.application.MedicalAssistantUseCase;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.multiagent.dto.MedicalAssistantRunRequest;
import com.aicode.framework.multiagent.dto.MedicalAssistantRunResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医疗助手 Multi Agent 接入。
 */
@RestController
@RequestMapping("/api/v1/multi-agent/medical-assistant")
public class MedicalAssistantController {

    private final MedicalAssistantUseCase medicalAssistantUseCase;

    public MedicalAssistantController(MedicalAssistantUseCase medicalAssistantUseCase) {
        this.medicalAssistantUseCase = medicalAssistantUseCase;
    }

    /** 提交 patientId 启动 Supervisor 多 Agent 流程。 */
    @PostMapping("/runs")
    public ApiResponse<MedicalAssistantRunResponse> run(@Valid @RequestBody MedicalAssistantRunRequest request) {
        MedicalAssistantResult result = medicalAssistantUseCase.run(request.patientId(), request.task());
        return ApiResponse.success(MedicalAssistantRunResponse.from(result));
    }
}
