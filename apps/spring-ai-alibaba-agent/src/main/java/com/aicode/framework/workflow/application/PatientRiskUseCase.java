package com.aicode.framework.workflow.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.workflow.domain.PatientRiskWorkflow;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 患者风险分析用例：校验 patientId → 交给 {@link PatientRiskWorkflow} 执行 → 返回结果。
 */
@Service
public class PatientRiskUseCase {

    private final PatientRiskWorkflow workflow;

    public PatientRiskUseCase(PatientRiskWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * @param patientId 患者编号
     * @return 患者/指标/风险/报告汇总结果
     * @throws InvalidChatRequestException patientId 空白
     */
    public PatientRiskWorkflowResult run(String patientId) {
        String content = patientId == null ? "" : patientId.trim();
        if (content.isEmpty()) {
            throw new InvalidChatRequestException("patientId must not be blank");
        }
        return workflow.run(UUID.randomUUID().toString(), content);
    }
}
