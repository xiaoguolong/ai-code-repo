package com.aicode.enterprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第4周 enterprise-knowledge-agent 启动类。多用户企业知识库：登录、知识库、文档、RAG 问答、历史与 Token 统计。
 * 横切能力由 ai-core（com.aicode.core）提供，通过组件扫描引入。
 */
@SpringBootApplication(scanBasePackages = {"com.aicode.enterprise", "com.aicode.core"})
public class EnterpriseKnowledgeAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseKnowledgeAgentApplication.class, args);
    }
}
