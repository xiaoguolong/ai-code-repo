package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.PlatformToolDefinition;
import com.aicode.framework.platform.domain.port.ToolCatalogPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Tool 目录管理用例。
 */
@Service
public class PlatformToolUseCase {

    private final ToolCatalogPort toolCatalogPort;

    public PlatformToolUseCase(ToolCatalogPort toolCatalogPort) {
        this.toolCatalogPort = toolCatalogPort;
    }

    /** 注册 Tool 元数据。 */
    public PlatformToolDefinition register(String toolKey, String toolName, String description) {
        String key = requireKey(toolKey, "toolKey");
        if (toolCatalogPort.findByKey(key).isPresent()) {
            throw new PlatformConflictException("tool already registered: " + key);
        }
        PlatformToolDefinition tool = new PlatformToolDefinition(
                key,
                requireText(toolName, "toolName"),
                description == null ? "" : description.trim(),
                true,
                Instant.now());
        toolCatalogPort.save(tool);
        return tool;
    }

    /** 列出 Tool。 */
    public List<PlatformToolDefinition> listTools() {
        return toolCatalogPort.listAll();
    }

    /** 查询 Tool。 */
    public PlatformToolDefinition getTool(String toolKey) {
        return toolCatalogPort.findByKey(requireKey(toolKey, "toolKey"))
                .orElseThrow(() -> new PlatformNotFoundException("tool not found: " + toolKey));
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
