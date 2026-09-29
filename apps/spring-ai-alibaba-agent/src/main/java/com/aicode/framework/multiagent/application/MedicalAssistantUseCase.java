package com.aicode.framework.multiagent.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.multiagent.domain.MedicalAssistantSupervisorGraph;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 医疗助手多 Agent 用例：校验入参 → 启动 Supervisor Graph。
 */
@Service
public class MedicalAssistantUseCase {

    private static final String DEFAULT_TASK_TEMPLATE = "为患者 %s 进行综合分析并生成随访计划";

    private final MedicalAssistantSupervisorGraph graph;

    public MedicalAssistantUseCase(MedicalAssistantSupervisorGraph graph) {
        this.graph = graph;
    }

    /**
     * 启动医疗助手多 Agent 流程。
     *
     * @throws InvalidChatRequestException patientId 空白
     */
    public MedicalAssistantResult run(String patientId, String task) {
        String normalizedPatientId = requirePatientId(patientId);
        String normalizedTask = normalizeTask(normalizedPatientId, task);
        return graph.run(UUID.randomUUID().toString(), normalizedPatientId, normalizedTask);
    }

    private String normalizeTask(String patientId, String task) {
        if (task != null && !task.trim().isEmpty()) {
            return task.trim();
        }
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
