package com.aicode.framework.workflow.domain.model;

import com.aicode.core.domain.model.TokenUsage;

/**
 * 患者风险分析 Workflow 运行结果。
 *
 * @param workflowId    流程标识
 * @param patientId     患者编号
 * @param patient       患者基础信息
 * @param metrics       健康指标
 * @param riskLevel     风险等级
 * @param justification 判断依据
 * @param escalated     是否加急（高风险为 true）
 * @param report        报告文本（模型生成）
 * @param usage         汇总 Token 用量
 * @param model         报告生成所用模型名
 */
public record PatientRiskWorkflowResult(
        String workflowId,
        String patientId,
        PatientProfile patient,
        HealthMetrics metrics,
        RiskLevel riskLevel,
        String justification,
        boolean escalated,
        String report,
        TokenUsage usage,
        String model
) {
}
