package com.aicode.framework.workflow.controller;

import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import com.aicode.framework.workflow.dto.PatientRiskRunRequest;
import com.aicode.framework.workflow.dto.PatientRiskRunResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者风险分析 Workflow 接入。只做校验与协议转换，编排在应用层与领域层。
 */
@RestController
@RequestMapping("/api/v1/workflows/patient-risk")
public class PatientRiskWorkflowController {

    private final PatientRiskUseCase patientRiskUseCase;

    public PatientRiskWorkflowController(PatientRiskUseCase patientRiskUseCase) {
        this.patientRiskUseCase = patientRiskUseCase;
    }

    /**
     * 提交患者编号并同步执行风险分析 Workflow，返回患者/指标/风险/报告。
     */
    @PostMapping("/runs")
    public ApiResponse<PatientRiskRunResponse> run(@Valid @RequestBody PatientRiskRunRequest request) {
        PatientRiskWorkflowResult result = patientRiskUseCase.run(request.patientId());
        return ApiResponse.success(PatientRiskRunResponse.from(result));
    }
}
