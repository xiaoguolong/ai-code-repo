package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformWorkflowDefinition;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.WorkflowRegistryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Workflow 目录管理用例。
 */
@Service
public class PlatformWorkflowUseCase {

    private final WorkflowRegistryPort workflowRegistryPort;
    private final AgentRegistryPort agentRegistryPort;

    public PlatformWorkflowUseCase(
            WorkflowRegistryPort workflowRegistryPort,
            AgentRegistryPort agentRegistryPort
    ) {
        this.workflowRegistryPort = workflowRegistryPort;
        this.agentRegistryPort = agentRegistryPort;
    }

    /** 注册 Workflow 并绑定 Agent。 */
    public PlatformWorkflowDefinition register(
            String workflowKey, String name, String description, String boundAgentKey) {
        String key = requireKey(workflowKey, "workflowKey");
        String agentKey = requireKey(boundAgentKey, "boundAgentKey");
        if (workflowRegistryPort.findByKey(key).isPresent()) {
            throw new PlatformConflictException("workflow already registered: " + key);
        }
        if (agentRegistryPort.findByKey(agentKey).isEmpty()) {
            throw new PlatformNotFoundException("bound agent not found: " + agentKey);
        }
        PlatformWorkflowDefinition workflow = new PlatformWorkflowDefinition(
                key,
                requireText(name, "name"),
                description == null ? "" : description.trim(),
                agentKey,
                true,
                Instant.now());
        workflowRegistryPort.save(workflow);
        return workflow;
    }

    /** 列出 Workflow。 */
    public List<PlatformWorkflowDefinition> listWorkflows() {
        return workflowRegistryPort.listAll();
    }

    /** 查询 Workflow。 */
    public PlatformWorkflowDefinition getWorkflow(String workflowKey) {
        return workflowRegistryPort.findByKey(requireKey(workflowKey, "workflowKey"))
                .orElseThrow(() -> new PlatformNotFoundException("workflow not found: " + workflowKey));
    }

    private String requireKey(String value, String field) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) {
            throw new InvalidChatRequestException(field + " must not be blank");
        }
        return key;
    }

    private String requireText(String value, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new InvalidChatRequestException(field + " must not be blank");
        }
        return text;
    }
}
