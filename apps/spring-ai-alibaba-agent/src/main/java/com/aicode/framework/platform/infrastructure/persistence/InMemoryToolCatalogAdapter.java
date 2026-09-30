package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformToolDefinition;
import com.aicode.framework.platform.domain.port.ToolCatalogPort;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 Tool 目录（Week 12 V1）。
 */
@Component
public class InMemoryToolCatalogAdapter implements ToolCatalogPort {

    private final ConcurrentHashMap<String, PlatformToolDefinition> store = new ConcurrentHashMap<>();

    @Override
    public void save(PlatformToolDefinition tool) {
        store.put(tool.toolKey(), tool);
    }

    @Override
    public Optional<PlatformToolDefinition> findByKey(String toolKey) {
        return Optional.ofNullable(store.get(toolKey));
    }

    @Override
    public List<PlatformToolDefinition> listAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(PlatformToolDefinition::toolKey))
                .toList();
    }

    /** 测试/seed 用：是否为空。 */
    public boolean isEmpty() {
        return store.isEmpty();
    }
}
