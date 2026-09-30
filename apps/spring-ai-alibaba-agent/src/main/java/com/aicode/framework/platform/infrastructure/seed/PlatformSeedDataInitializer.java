package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformToolDefinition;
import com.aicode.framework.platform.domain.model.PlatformWorkflowDefinition;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.ToolCatalogPort;
import com.aicode.framework.platform.domain.port.WorkflowRegistryPort;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAgentRegistryAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryToolCatalogAdapter;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryWorkflowRegistryAdapter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 启动时预置 Week 8–11 既有 Agent / Tool / Workflow 元数据。
 */
@Component
public class PlatformSeedDataInitializer implements ApplicationRunner {

    private final AgentRegistryPort agentRegistryPort;
    private final ToolCatalogPort toolCatalogPort;
    private final WorkflowRegistryPort workflowRegistryPort;

    public PlatformSeedDataInitializer(
            AgentRegistryPort agentRegistryPort,
            ToolCatalogPort toolCatalogPort,
            WorkflowRegistryPort workflowRegistryPort
    ) {
        this.agentRegistryPort = agentRegistryPort;
        this.toolCatalogPort = toolCatalogPort;
        this.workflowRegistryPort = workflowRegistryPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (agentRegistryPort instanceof InMemoryAgentRegistryAdapter adapter && !adapter.isEmpty()) {
            return;
        }
        seedAgents();
        seedTools();
        seedWorkflows();
    }

    private void seedAgents() {
        Instant now = Instant.now();
        agentRegistryPort.save(new PlatformAgentDefinition(
                "framework-react", "Graph ReAct Agent", "Week 8 Spring AI Alibaba Graph Agent",
                AgentType.FRAMEWORK_REACT, PlatformAgentConfig.defaults(), now));
        agentRegistryPort.save(new PlatformAgentDefinition(
                "patient-risk", "患者风险 Workflow", "Week 9-10 固定 Workflow + HITL",
                AgentType.PATIENT_RISK_WORKFLOW, PlatformAgentConfig.defaults(), now));
        agentRegistryPort.save(new PlatformAgentDefinition(
                "medical-assistant", "医疗助手 Multi Agent", "Week 11 Supervisor 四角色",
                AgentType.MEDICAL_ASSISTANT, PlatformAgentConfig.defaults(), now));
    }

    private void seedTools() {
        Instant now = Instant.now();
        toolCatalogPort.save(new PlatformToolDefinition(
                "PatientLookupTool", "PatientLookupTool", "按患者编号查询患者基础信息", true, now));
        toolCatalogPort.save(new PlatformToolDefinition(
                "HealthMetricTool", "HealthMetricTool", "按患者编号查询健康指标", true, now));
    }

    private void seedWorkflows() {
        Instant now = Instant.now();
        workflowRegistryPort.save(new PlatformWorkflowDefinition(
                "patient-risk-workflow", "患者风险分析", "含 HIGH 风险 HITL",
                "patient-risk", true, now));
        workflowRegistryPort.save(new PlatformWorkflowDefinition(
                "medical-assistant-workflow", "医疗助手 Supervisor", "Supervisor + 四 Worker",
                "medical-assistant", true, now));
    }
}
