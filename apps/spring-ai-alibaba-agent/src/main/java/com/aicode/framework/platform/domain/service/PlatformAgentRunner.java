package com.aicode.framework.platform.domain.service;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.application.FrameworkAgentUseCase;
import com.aicode.framework.domain.model.FrameworkAgentResult;
import com.aicode.framework.multiagent.application.MedicalAssistantUseCase;
import com.aicode.framework.multiagent.domain.model.MedicalAssistantResult;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.model.PatientRiskWorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将平台 Agent 定义调度到 Week 8–11 既有运行时。
 */
@Service
public class PlatformAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAgentRunner.class);

    private final FrameworkAgentUseCase frameworkAgentUseCase;
    private final PatientRiskUseCase patientRiskUseCase;
    private final MedicalAssistantUseCase medicalAssistantUseCase;

    public PlatformAgentRunner(
            FrameworkAgentUseCase frameworkAgentUseCase,
            PatientRiskUseCase patientRiskUseCase,
            MedicalAssistantUseCase medicalAssistantUseCase
    ) {
        this.frameworkAgentUseCase = frameworkAgentUseCase;
        this.patientRiskUseCase = patientRiskUseCase;
        this.medicalAssistantUseCase = medicalAssistantUseCase;
    }

    /**
     * 按 Agent 类型执行并返回结构化输出。
     */
    public PlatformRunOutput run(PlatformAgentDefinition agent, Map<String, Object> input) {
        log.info("[platform] dispatch agentKey={} agentType={}", agent.agentKey(), agent.agentType());
        return switch (agent.agentType()) {
            case FRAMEWORK_REACT -> runFrameworkReact(input);
            case PATIENT_RISK_WORKFLOW -> runPatientRisk(input);
            case MEDICAL_ASSISTANT -> runMedicalAssistant(input);
        };
    }

    private PlatformRunOutput runFrameworkReact(Map<String, Object> input) {
        String task = requireString(input, "task");
        FrameworkAgentResult result = frameworkAgentUseCase.run(task);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskId", result.taskId());
        output.put("answer", result.answer());
        output.put("steps", result.steps());
        output.put("totalSteps", result.totalSteps());
        return new PlatformRunOutput(output, result.usage(), result.model());
    }

    private PlatformRunOutput runPatientRisk(Map<String, Object> input) {
        String patientId = requireString(input, "patientId");
        PatientRiskWorkflowResult result = patientRiskUseCase.run(patientId);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("workflowId", result.workflowId());
        output.put("status", result.status().name());
        output.put("patientId", result.patientId());
        output.put("riskLevel", result.riskLevel().name());
        output.put("report", result.report());
        output.put("escalated", result.escalated());
        return new PlatformRunOutput(output, result.usage(), result.model());
    }

    private PlatformRunOutput runMedicalAssistant(Map<String, Object> input) {
        String patientId = requireString(input, "patientId");
        String task = optionalString(input, "task");
        MedicalAssistantResult result = medicalAssistantUseCase.run(patientId, task);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("runId", result.runId());
        output.put("patientId", result.patientId());
        output.put("task", result.task());
        output.put("riskLevel", result.riskLevel().name());
        output.put("report", result.report());
        output.put("followUpPlan", result.followUpPlan());
        output.put("steps", result.steps());
        return new PlatformRunOutput(output, result.usage(), result.model());
    }

    private String requireString(Map<String, Object> input, String key) {
        Object value = input == null ? null : input.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new InvalidChatRequestException(key + " must not be blank");
        }
        return value.toString().trim();
    }

    private String optionalString(Map<String, Object> input, String key) {
        if (input == null) {
            return null;
        }
        Object value = input.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
