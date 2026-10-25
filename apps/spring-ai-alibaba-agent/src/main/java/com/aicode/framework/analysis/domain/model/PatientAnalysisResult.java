package com.aicode.framework.analysis.domain.model;

import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.aicode.framework.workflow.domain.model.RiskLevel;

import java.util.List;

/**
 * 患者分析结果（Week 21，Agent 1）。
 *
 * @param patientId    患者编号
 * @param patient      患者基础信息
 * @param metrics      健康指标
 * @param reports      检查报告列表
 * @param riskLevel    风险等级（规则判定，非模型判定）
 * @param justification 判断依据
 * @param report       分析报告正文
 * @param degraded     是否走了降级（模型不可用时为规则模板，**不冒充模型输出**）
 * @param memoryId     写入的长期记忆 ID；未写入为 null
 * @param steps        步骤轨迹
 * @param usage        Token 用量（降级时为 unknown）
 * @param model        模型名
 */
public record PatientAnalysisResult(
        String patientId,
        PatientProfile patient,
        HealthMetrics metrics,
        List<ReportSummary> reports,
        RiskLevel riskLevel,
        String justification,
        String report,
        boolean degraded,
        String memoryId,
        List<PatientAnalysisStep> steps,
        TokenUsage usage,
        String model
) {

    public PatientAnalysisResult {
        reports = reports == null ? List.of() : List.copyOf(reports);
        steps = steps == null ? List.of() : List.copyOf(steps);
        usage = usage == null ? TokenUsage.unknown() : usage;
    }

    /**
     * 是否需要人工审核后才写随访记忆（Week 23）。
     *
     * <p>只有 HIGH 触发 HITL；中/低风险保持第 21 周行为（直接写记忆）。</p>
     *
     * @return true = HIGH 风险，调用方不得直接写记忆
     */
    public boolean requiresApproval() {
        return riskLevel == RiskLevel.HIGH;
    }

    /**
     * 复制一份并挂上记忆 ID（记忆写入发生在 Agent 之外，由用例层负责）。
     *
     * @param memoryId 记忆 ID
     * @return 新结果
     */
    public PatientAnalysisResult withMemoryId(String memoryId) {
        return new PatientAnalysisResult(
                patientId, patient, metrics, reports, riskLevel, justification, report,
                degraded, memoryId, steps, usage, model);
    }
}
