package com.aicode.demo.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天应用配置。
 *
 * @param systemPromptVersion 系统提示文件版本，对应 prompts/system-{version}.txt，默认 v1
 * @param memoryProvider      memory 或 redis，默认 memory
 * @param maxMemoryMessages   短期记忆条数上限，默认 20
 */
@ConfigurationProperties(prefix = "chat")
public record ChatAppProperties(
        String systemPromptVersion,
        String memoryProvider,
        Integer maxMemoryMessages
) {

    /**
     * @return 合法窗口大小，缺省或非法时回退 20
     */
    public int resolvedMaxMemoryMessages() {
        if (maxMemoryMessages == null || maxMemoryMessages <= 0) {
            return 20;
        }
        return maxMemoryMessages;
    }
}
