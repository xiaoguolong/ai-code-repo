package com.aicode.demo.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天应用配置。
 *
 * @param systemPromptVersion 系统提示文件版本，对应 prompts/system-{version}.txt，默认 v1
 */
@ConfigurationProperties(prefix = "chat")
public record ChatAppProperties(String systemPromptVersion) {
}
