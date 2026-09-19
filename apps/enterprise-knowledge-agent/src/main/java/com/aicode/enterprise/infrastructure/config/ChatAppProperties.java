package com.aicode.enterprise.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天应用配置。
 *
 * @param dbType Fluent-MyBatis 数据库类型，默认 postgre_sql
 */
@ConfigurationProperties(prefix = "chat")
public record ChatAppProperties(String dbType) {

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
