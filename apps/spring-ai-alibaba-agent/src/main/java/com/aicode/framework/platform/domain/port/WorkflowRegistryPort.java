package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformWorkflowDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Workflow 目录端口。
 */
public interface WorkflowRegistryPort {

    void save(PlatformWorkflowDefinition workflow);

    Optional<PlatformWorkflowDefinition> findByKey(String workflowKey);

    List<PlatformWorkflowDefinition> listAll();
}
