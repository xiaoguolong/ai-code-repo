package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Agent 注册与配置用例。
 */
@Service
public class PlatformAgentUseCase {

    private final AgentRegistryPort agentRegistryPort;

    public PlatformAgentUseCase(AgentRegistryPort agentRegistryPort) {
        this.agentRegistryPort = agentRegistryPort;
    }

    /** 注册 Agent。 */
    public PlatformAgentDefinition register(
            String agentKey, String name, String description, AgentType agentType) {
        String key = requireKey(agentKey);
        if (agentRegistryPort.findByKey(key).isPresent()) {
            throw new PlatformConflictException("agent already registered: " + key);
        }
        PlatformAgentDefinition agent = new PlatformAgentDefinition(
                key,
                requireText(name, "name"),
                description == null ? "" : description.trim(),
                agentType,
                PlatformAgentConfig.defaults(),
                Instant.now());
        agentRegistryPort.save(agent);
        return agent;
    }

    /** 列出所有 Agent。 */
    public List<PlatformAgentDefinition> listAgents() {
        return agentRegistryPort.listAll();
    }

    /** 查询 Agent。 */
    public PlatformAgentDefinition getAgent(String agentKey) {
        return agentRegistryPort.findByKey(requireKey(agentKey))
                .orElseThrow(() -> new PlatformNotFoundException("agent not found: " + agentKey));
    }

    /** 更新 Agent 配置。 */
    public PlatformAgentDefinition updateConfig(String agentKey, PlatformAgentConfig config) {
        PlatformAgentDefinition existing = getAgent(agentKey);
        agentRegistryPort.updateConfig(existing.agentKey(), config);
        return agentRegistryPort.findByKey(existing.agentKey()).orElseThrow();
    }

    private String requireKey(String agentKey) {
        String key = agentKey == null ? "" : agentKey.trim();
        if (key.isEmpty()) {
            throw new InvalidChatRequestException("agentKey must not be blank");
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
