package com.aicode.core.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * 工具定义（名称、描述与 JSON Schema 参数），供模型选工具。parameters 为 JSON Schema 对象，
 * 描述参数类型、必填字段与约束，模型据此生成合法参数。
 */
public record ToolDefinition(String name, String description, Map<String, Object> parameters) {

    public ToolDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (parameters == null) {
            parameters = Map.of();
        }
        parameters = Map.copyOf(parameters);
    }
}
