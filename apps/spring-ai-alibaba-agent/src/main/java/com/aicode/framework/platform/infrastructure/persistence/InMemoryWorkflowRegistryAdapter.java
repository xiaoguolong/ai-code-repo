package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformWorkflowDefinition;
import com.aicode.framework.platform.domain.port.WorkflowRegistryPort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 Workflow 目录（Week 12 V1）。
 */
@Component
public class InMemoryWorkflowRegistryAdapter implements WorkflowRegistryPort {

    private final ConcurrentHashMap<String, PlatformWorkflowDefinition> store = new ConcurrentHashMap<>();

    @Override
    public void save(PlatformWorkflowDefinition workflow) {
        store.put(workflow.workflowKey(), workflow);
    }

    @Override
    public Optional<PlatformWorkflowDefinition> findByKey(String workflowKey) {
        return Optional.ofNullable(store.get(workflowKey));
    }

    @Override
    public List<PlatformWorkflowDefinition> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(PlatformWorkflowDefinition::workflowKey))
                .toList();
    }

    /** 测试/seed 用：是否为空。 */
    public boolean isEmpty() {
        return store.isEmpty();
    }
}
