package com.aicode.framework.platform.infrastructure.config;

import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.security.DirectRunAuthInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 平台模块配置装配。
 */
@Configuration
@EnableConfigurationProperties(PlatformSecurityProperties.class)
public class PlatformConfiguration {

    @Bean
    DirectRunAuthInterceptor directRunAuthInterceptor(
            PlatformSecurityProperties securityProperties,
            PlatformPermissionChecker permissionChecker
    ) {
        return new DirectRunAuthInterceptor(securityProperties, permissionChecker);
    }
}
