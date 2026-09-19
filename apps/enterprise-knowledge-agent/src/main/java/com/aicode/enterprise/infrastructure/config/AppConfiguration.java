package com.aicode.enterprise.infrastructure.config;

import cn.org.atool.fluent.mybatis.metadata.DbType;
import cn.org.atool.fluent.mybatis.spring.MapperFactory;
import com.aicode.core.domain.FileUrlAssembler;
import com.aicode.core.domain.OcrMarkdownImageProcessor;
import com.aicode.core.domain.port.FileStoragePort;
import com.aicode.core.domain.port.ImageDownloadPort;
import com.aicode.core.infrastructure.config.LlmProperties;
import com.aicode.core.infrastructure.config.RagProperties;
import com.aicode.enterprise.application.ChatRuntimeConfig;
import com.aicode.enterprise.application.RagRuntimeConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 企业知识库应用装配：时钟、运行时配置、领域服务、MyBatis Mapper 扫描。
 * 模型/Prompt/Embedding/向量/OCR/文件/鉴权等横切装配已收敛到 ai-core 的 {@code AiCoreConfiguration}。
 */
@Configuration
@EnableConfigurationProperties({ChatAppProperties.class})
@MapperScan("com.aicode.enterprise.infrastructure.persistence.mapper")
public class AppConfiguration {

    /**
     * Fluent-MyBatis MapperFactory。指定数据库类型，避免默认 MySQL 方言的反引号。
     */
    @Bean
    MapperFactory mapperFactory(ChatAppProperties chatAppProperties) {
        DbType dbType = DbType.valueOf(chatAppProperties.resolvedDbType().toUpperCase());
        return new MapperFactory().dbType(dbType);
    }

    /**
     * UTC 时钟，便于单测注入 fixed Clock。
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 把 LLM 配置映射为用例所需的运行时参数。
     */
    @Bean
    ChatRuntimeConfig chatRuntimeConfig(LlmProperties llmProperties) {
        return new ChatRuntimeConfig(
                llmProperties.model(),
                llmProperties.temperature(),
                llmProperties.maxTokens()
        );
    }

    /**
     * 把 RAG 配置映射为用例所需的运行时参数。
     */
    @Bean
    RagRuntimeConfig ragRuntimeConfig(RagProperties ragProperties) {
        return new RagRuntimeConfig(
                ragProperties.resolvedDefaultTopK(),
                ragProperties.resolvedChunkSize(),
                ragProperties.resolvedChunkOverlap()
        );
    }

    /**
     * OCR 图片占位符清理器。领域服务，依赖下载端口与文件存储端口。
     */
    @Bean
    OcrMarkdownImageProcessor ocrMarkdownImageProcessor(
            ImageDownloadPort imageDownloadPort,
            FileStoragePort fileStoragePort,
            FileUrlAssembler urlAssembler
    ) {
        return new OcrMarkdownImageProcessor(imageDownloadPort, fileStoragePort, urlAssembler);
    }
}
