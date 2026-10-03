package com.aicode.framework.platform.infrastructure.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 落库适配器装配（Week 16）。
 *
 * <p>按规范 5.8.1：仅落库模式使用、且依赖 JDBC 的组件必须同时加
 * {@code @ConditionalOnClass}（字符串类名，避免类缺失时 NoClassDefFoundError）
 * 与 {@code @ConditionalOnProperty}。四个 Jdbc*Adapter 自身也带同样注解，
 * 这里只负责构造它们共用的 {@link PlatformJdbcSupport}。</p>
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "jdbc", matchIfMissing = true)
public class PlatformJdbcConfiguration {

    /**
     * JDBC 支撑组件：数据源访问、双库通用主键生成与时间类型转换。
     *
     * @param jdbcTemplate 由 Spring Boot 数据源自动配置提供
     * @return JDBC 支撑实例
     */
    @Bean
    public PlatformJdbcSupport platformJdbcSupport(JdbcTemplate jdbcTemplate) {
        return new PlatformJdbcSupport(jdbcTemplate);
    }
}
