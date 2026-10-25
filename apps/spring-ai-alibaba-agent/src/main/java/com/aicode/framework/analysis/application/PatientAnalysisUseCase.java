package com.aicode.framework.analysis.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.analysis.domain.PatientAnalysisAgent;
import com.aicode.framework.analysis.domain.model.PatientAnalysisResult;
import com.aicode.framework.platform.application.PlatformMemoryUseCase;
import com.aicode.framework.platform.domain.model.PlatformMemoryRecord;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 患者分析用例（Week 21，Agent 1）：入参校验 → Agent 执行 → 写长期记忆。
 *
 * <p>记忆写入在这里而不是 Agent 里：Agent 是纯领域编排（拿不到租户），
 * scope 由调用方从登录态推导后传入，避免领域层被 Web 上下文污染。</p>
 *
 * <p>记忆写入失败只记 WARN 并返回 {@code memoryId=null}：报告已经产出，
 * 不能因为「记不下来」而让用户白等一次模型调用。</p>
 */
@Service
public class PatientAnalysisUseCase {

    private static final Logger log = LoggerFactory.getLogger(PatientAnalysisUseCase.class);

    private static final String DEFAULT_TASK_TEMPLATE = "为患者 %s 生成慢病风险分析报告";

    private final PatientAnalysisAgent agent;
    private final PlatformMemoryUseCase memoryUseCase;

    public PatientAnalysisUseCase(PatientAnalysisAgent agent, PlatformMemoryUseCase memoryUseCase) {
        this.agent = agent;
        this.memoryUseCase = memoryUseCase;
    }

    /**
     * 执行患者分析并按需写入记忆。
     *
     * @param patientId 患者编号
     * @param task      任务描述，空白时用默认模板
     * @param scope     记忆范围；为空（非平台调度）时不写记忆
     * @return 分析结果（可能带 memoryId）
     * @throws InvalidChatRequestException patientId 空白
     */
    public PatientAnalysisResult run(String patientId, String task, PlatformMemoryScope scope) {
        String normalizedPatientId = requirePatientId(patientId);
        String normalizedTask = normalizeTask(normalizedPatientId, task);
        PatientAnalysisResult result = agent.run(normalizedPatientId, normalizedTask);
        if (scope == null) {
            return result;
        }
        if (result.requiresApproval()) {
            // Week 23 HITL：HIGH 风险先进入审批，批准后才写随访记忆。
            log.info("[patient-analysis] high risk requires approval patientId={} userId={} risk={}",
                    normalizedPatientId, scope.userId(), result.riskLevel().name());
            return result;
        }
        try {
            PlatformMemoryRecord saved = memoryUseCase.save(
                    scope, normalizedPatientId, normalizedTask, result.report());
            return result.withMemoryId(saved.memoryId());
        } catch (RuntimeException ex) {
            log.warn("[patient-analysis] memory save failed patientId={} userId={} error={}",
                    normalizedPatientId, scope.userId(), ex.getMessage());
            return result;
        }
    }

    private String normalizeTask(String patientId, String task) {
        if (task != null && !task.trim().isEmpty()) {
            return task.trim();
        }
        return defaultTask(patientId);
    }

    /**
     * 默认任务文案（Week 23：平台创建审批任务时也要用同一份，避免两处措辞漂移）。
     *
     * @param patientId 患者编号
     * @return 默认任务描述
     */
    public static String defaultTask(String patientId) {
        return DEFAULT_TASK_TEMPLATE.formatted(patientId);
    }

    private String requirePatientId(String patientId) {
        String content = patientId == null ? "" : patientId.trim();
        if (content.isEmpty()) {
            throw new InvalidChatRequestException("patientId must not be blank");
        }
        return content;
    }
}
