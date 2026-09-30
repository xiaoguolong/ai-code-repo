package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 Agent 注册表（Week 12 V1）。
 */
@Component
public class InMemoryAgentRegistryAdapter implements AgentRegistryPort {

    private final ConcurrentHashMap<String, PlatformAgentDefinition> store = new ConcurrentHashMap<>();

    @Override
    public void save(PlatformAgentDefinition agent) {
        store.put(agent.agentKey(), agent);
    }

    @Override
    public Optional<PlatformAgentDefinition> findByKey(String agentKey) {
        return Optional.ofNullable(store.get(agentKey));
    }

    @Override
    public List<PlatformAgentDefinition> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(PlatformAgentDefinition::agentKey))
                .toList();
    }

    @Override
    public void updateConfig(String agentKey, PlatformAgentConfig config) {
        PlatformAgentDefinition existing = store.get(agentKey);
        if (existing == null) {
            return;
        }
        store.put(agentKey, new PlatformAgentDefinition(
                existing.agentKey(), existing.name(), existing.description(),
                existing.agentType(), config, existing.registeredAt()));
    }

    /** 测试/seed 用：是否为空。 */
    public boolean isEmpty() {
        return store.isEmpty();
    }
}
