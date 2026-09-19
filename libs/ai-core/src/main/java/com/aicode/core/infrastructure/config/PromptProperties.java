package com.aicode.core.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prompt 模板版本配置。统一替代各模块自有的 prompt 版本字段。
 *
 * @param version 系统提示文件版本，对应 prompts/{name}-{version}.txt，默认 v1
 */
@ConfigurationProperties(prefix = "prompt")
public record PromptProperties(String version) {

    /**
     * @return 有效版本，缺省 v1
     */
    public String resolvedVersion() {
        return version == null || version.isBlank() ? "v1" : version.trim();
    }
}
