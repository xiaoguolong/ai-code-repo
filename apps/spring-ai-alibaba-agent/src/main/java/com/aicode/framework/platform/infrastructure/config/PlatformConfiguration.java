package com.aicode.framework.platform.infrastructure.config;

import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.security.DirectRunAuthInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 平台模块配置装配。
 */
@Configuration
@EnableConfigurationProperties({
        PlatformSecurityProperties.class,
        PlatformMemoryProperties.class,
        PlatformRateLimitProperties.class
})
public class PlatformConfiguration {

    @Bean
    DirectRunAuthInterceptor directRunAuthInterceptor(
            PlatformSecurityProperties securityProperties,
            PlatformPermissionChecker permissionChecker
    ) {
        return new DirectRunAuthInterceptor(securityProperties, permissionChecker);
    }

    /**
     * UTC 时钟（Week 22）：登录限流的窗口/封禁判断依赖它，单测可注入固定时钟。
     *
     * @return 系统 UTC 时钟
     */
    @Bean
    Clock platformClock() {
        return Clock.systemUTC();
    }
}
