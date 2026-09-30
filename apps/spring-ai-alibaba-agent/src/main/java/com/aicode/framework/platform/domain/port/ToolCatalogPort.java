package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformToolDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Tool 目录端口。
 */
public interface ToolCatalogPort {

    void save(PlatformToolDefinition tool);

    Optional<PlatformToolDefinition> findByKey(String toolKey);

    List<PlatformToolDefinition> listAll();
}
