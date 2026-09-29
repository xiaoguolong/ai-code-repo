package com.aicode.framework.workflow.controller;

import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.dto.PatientRiskResumeRequest;
import com.aicode.framework.workflow.dto.PatientRiskRunRequest;
import com.aicode.framework.workflow.dto.PatientRiskRunResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者风险分析 Workflow 接入。启动、查询与人工审核恢复。
 */
@RestController
@RequestMapping("/api/v1/workflows/patient-risk")
public class PatientRiskWorkflowController {

    private final PatientRiskUseCase patientRiskUseCase;

    public PatientRiskWorkflowController(PatientRiskUseCase patientRiskUseCase) {
        this.patientRiskUseCase = patientRiskUseCase;
    }

    /** 提交 patientId 启动 Workflow；HIGH 风险暂停待审。 */
    @PostMapping("/runs")
    public ApiResponse<PatientRiskRunResponse> run(@Valid @RequestBody PatientRiskRunRequest request) {
        PatientRiskWorkflowResult result = patientRiskUseCase.run(request.patientId());
        return ApiResponse.success(PatientRiskRunResponse.from(result));
    }

    /** 查询 Workflow 当前状态。 */
    @GetMapping("/runs/{workflowId}")
    public ApiResponse<PatientRiskRunResponse> getRun(@PathVariable String workflowId) {
        PatientRiskWorkflowResult result = patientRiskUseCase.getRun(workflowId);
        return ApiResponse.success(PatientRiskRunResponse.from(result));
    }

    /** 人工审核后恢复 Workflow。 */
    @PostMapping("/runs/{workflowId}/resume")
    public ApiResponse<PatientRiskRunResponse> resume(
            @PathVariable String workflowId,
            @Valid @RequestBody PatientRiskResumeRequest request
    ) {
        PatientRiskWorkflowResult result = patientRiskUseCase.resume(workflowId, request.approved());
        return ApiResponse.success(PatientRiskRunResponse.from(result));
    }
}
