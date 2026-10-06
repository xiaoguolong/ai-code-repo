package com.aicode.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 网关应用入口（Week 19）。
 *
 * <p>职责边界（规范 5.2）：统一入口与路由、会话鉴权、链路透传，<b>不含任何业务规则</b>。
 * 业务裁决仍由平台（8084）负责，网关只保存「网关联会话 → 平台 token」映射。</p>
 *
 * <p>技术栈为响应式（Spring Cloud Gateway + WebFlux），因此业务模块里的 Servlet 技术栈
 * 不得进入本模块 classpath（见 {@code pom.xml} 对 {@code spring-boot-starter-web} 的排除说明）。</p>
 */
@SpringBootApplication
@EnableFeignClients
public class GatewayApplication {

    /**
     * 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
