package com.aicode.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 第6周 patient-agent 启动类。原生 Function Calling Agent：用户任务 → 模型选工具 → 执行工具 → 观察结果 → 最终答案与轨迹。
 * 模型/Prompt/工具契约等横切能力由 ai-core（com.aicode.core）提供，通过组件扫描引入。
 * 本应用无数据库，显式排除 DataSource 自动配置，避免环境残留触发数据源装配。
 */
@SpringBootApplication(
        scanBasePackages = {"com.aicode.patient", "com.aicode.core"},
        exclude = DataSourceAutoConfiguration.class
)
public class PatientAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientAgentApplication.class, args);
    }
}
