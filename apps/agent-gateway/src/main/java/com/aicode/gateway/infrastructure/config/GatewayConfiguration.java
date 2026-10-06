package com.aicode.gateway.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 网关配置装配（Week 19）。
 *
 * <p>本模块的装配面很窄：除 Feign 客户端配置（{@code PlatformFeignConfiguration}）外，
 * 只有三组配置属性需要绑定。其余能力（会话 / 缓存 / 鉴权 / 链路）都是
 * {@code @Component} 自注册，无需在这里逐个 new，避免出现「装配类变成上帝类」。</p>
 */
@Configuration
@EnableConfigurationProperties({
        GatewayProperties.class,
        GatewaySessionProperties.class,
        GatewayCatalogProperties.class
})
public class GatewayConfiguration {
}
