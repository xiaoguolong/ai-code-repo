package com.aicode.demo.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天应用配置。
 *
 * @param systemPromptVersion 系统提示文件版本，对应 prompts/system-{version}.txt，默认 v1
 * @param memoryProvider      memory 或 redis，默认 memory
 * @param maxMemoryMessages   短期记忆条数上限，默认 20
 * @param dbType              Fluent-MyBatis 数据库类型，默认 postgre_sql
 */
@ConfigurationProperties(prefix = "chat")
public record ChatAppProperties(
        String systemPromptVersion,
        String memoryProvider,
        Integer maxMemoryMessages,
        String dbType
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

    /**
     * @return 数据库类型字符串，缺省 postgre_sql
     */
    public String resolvedDbType() {
        if (dbType == null || dbType.isBlank()) {
            return "postgre_sql";
        }
        return dbType.trim();
    }
}
