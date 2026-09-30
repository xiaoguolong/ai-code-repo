package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;

import java.util.List;
import java.util.Optional;

/**
 * Agent 注册表端口。
 */
public interface AgentRegistryPort {

    void save(PlatformAgentDefinition agent);

    Optional<PlatformAgentDefinition> findByKey(String agentKey);

    List<PlatformAgentDefinition> listAll();

    void updateConfig(String agentKey, PlatformAgentConfig config);
}
