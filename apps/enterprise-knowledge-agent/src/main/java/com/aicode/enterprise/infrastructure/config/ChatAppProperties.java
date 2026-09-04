package com.aicode.enterprise.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天应用配置。
 *
 * @param systemPromptVersion 系统提示文件版本，对应 prompts/{name}-{version}.txt，默认 v1
 * @param dbType              Fluent-MyBatis 数据库类型，默认 postgre_sql
 */
@ConfigurationProperties(prefix = "chat")
public record ChatAppProperties(String systemPromptVersion, String dbType) {

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
