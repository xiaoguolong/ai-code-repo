package com.aicode.framework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI Alibaba Agent 启动类（第 8 周）。以 Spring AI Alibaba Graph 编排「模型带工具循环」，
 * 模型经 Spring AI（OpenAI 兼容端点指向 DeepSeek）适配，工具复用 ai-core 契约与 ToolRegistry。
 * 模型/Prompt/工具契约等横切能力由 ai-core（com.aicode.core）提供，通过组件扫描引入。
 *
 * <p>Week 16 起本应用接入 PostgreSQL（Flyway 迁移平台 RBAC / 执行记录 / 审计）。
 * 数据源与迁移只在 {@code jdbc} profile 下启用（{@code spring.profiles.default=jdbc}）；
 * {@code platform.persistence.mode=memory} 时改用进程内适配器，无需数据库即可启动。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.aicode.framework", "com.aicode.core"})
public class SpringAiAlibabaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiAlibabaAgentApplication.class, args);
    }
}
