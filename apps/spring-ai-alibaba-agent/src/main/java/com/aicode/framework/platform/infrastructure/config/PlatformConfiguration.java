package com.aicode.framework.platform.infrastructure.config;

import com.aicode.framework.platform.domain.service.PlatformPermissionChecker;
import com.aicode.framework.platform.infrastructure.security.DirectRunAuthInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * 平台模块配置装配。
 *
 * <p>Week 23 起开启 Spring 调度：长期记忆过期清理需要一个每日任务；
 * 单测通过 {@code platform.memory.lifecycle.cleanup-enabled=false} 关闭，避免后台线程干扰。</p>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        PlatformSecurityProperties.class,
        PlatformMemoryProperties.class,
        PlatformRateLimitProperties.class,
        PlatformMemoryLifecycleProperties.class,
        PlatformOperationsProperties.class,
        PlatformFollowUpProperties.class,
        PlatformApprovalProperties.class,
        PlatformKnowledgeProperties.class
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
