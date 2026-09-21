package com.aicode.framework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Spring AI Alibaba Agent 启动类（第 8 周）。以 Spring AI Alibaba Graph 编排「模型带工具循环」，
 * 模型经 Spring AI（OpenAI 兼容端点指向 DeepSeek）适配，工具复用 ai-core 契约与 ToolRegistry。
 * 模型/Prompt/工具契约等横切能力由 ai-core（com.aicode.core）提供，通过组件扫描引入。
 * 本应用无数据库，显式排除 DataSource 自动配置。
 */
@SpringBootApplication(
        scanBasePackages = {"com.aicode.framework", "com.aicode.core"},
        exclude = DataSourceAutoConfiguration.class
)
public class SpringAiAlibabaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiAlibabaAgentApplication.class, args);
    }
}
