package com.aicode.framework.workflow.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.workflow.domain.PatientRiskWorkflow;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 患者风险分析用例：校验入参 → 启动/恢复/查询 Workflow。
 */
@Service
public class PatientRiskUseCase {

    private final PatientRiskWorkflow workflow;

    public PatientRiskUseCase(PatientRiskWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * 启动风险分析流程。
     *
     * @throws InvalidChatRequestException patientId 空白
     */
    public PatientRiskWorkflowResult run(String patientId) {
        String content = requirePatientId(patientId);
        return workflow.start(UUID.randomUUID().toString(), content);
    }

    /**
     * 人工审核后恢复流程。
     *
     * @throws InvalidChatRequestException workflowId 空白
     */
    public PatientRiskWorkflowResult resume(String workflowId, boolean approved) {
        return workflow.resume(requireWorkflowId(workflowId), approved);
    }

    /**
     * 查询流程当前状态。
     *
     * @throws InvalidChatRequestException workflowId 空白
     */
    public PatientRiskWorkflowResult getRun(String workflowId) {
        return workflow.getRun(requireWorkflowId(workflowId));
    }

    private String requirePatientId(String patientId) {
        String content = patientId == null ? "" : patientId.trim();
        if (content.isEmpty()) {
            throw new InvalidChatRequestException("patientId must not be blank");
        }
        return content;
    }

    private String requireWorkflowId(String workflowId) {
        String content = workflowId == null ? "" : workflowId.trim();
        if (content.isEmpty()) {
            throw new InvalidChatRequestException("workflowId must not be blank");
        }
        return content;
    }
}
