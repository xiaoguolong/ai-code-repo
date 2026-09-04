package com.aicode.enterprise.infrastructure.config;

import cn.org.atool.fluent.mybatis.metadata.DbType;
import cn.org.atool.fluent.mybatis.spring.MapperFactory;
import com.aicode.enterprise.application.ChatRuntimeConfig;
import com.aicode.enterprise.application.RagRuntimeConfig;
import com.aicode.enterprise.domain.FileUrlAssembler;
import com.aicode.enterprise.domain.OcrMarkdownImageProcessor;
import com.aicode.enterprise.domain.port.FileStoragePort;
import com.aicode.enterprise.domain.port.ImageDownloadPort;
import com.aicode.enterprise.infrastructure.llm.OpenAiChatResponseMapper;
import com.aicode.enterprise.infrastructure.ocr.PaddleOcrResponseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * 基础设施装配：时钟、RestClient、运行时配置、领域服务、MyBatis Mapper 扫描。密钥不在此打印。
 */
@Configuration
@EnableConfigurationProperties({
        LlmProperties.class,
        ChatAppProperties.class,
        EmbeddingProperties.class,
        RagProperties.class,
        OcrProperties.class,
        AuthProperties.class,
        FileProperties.class
})
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
     * JSON 映射器，从厂商响应中提取 content 与 usage。
     */
    @Bean
    OpenAiChatResponseMapper openAiChatResponseMapper() {
        return new OpenAiChatResponseMapper();
    }

    /**
     * OCR 响应映射器，从千帆响应中提取 markdown.text 与 images。
     */
    @Bean
    PaddleOcrResponseMapper paddleOcrResponseMapper() {
        return new PaddleOcrResponseMapper();
    }

    /**
     * 文件访问路径组装器。域名来自 file.public-base-url。
     */
    @Bean
    FileUrlAssembler fileUrlAssembler(FileProperties fileProperties) {
        return new FileUrlAssembler(fileProperties.resolvedPublicBaseUrl());
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

    /**
     * LLM HTTP 客户端。Authorization 在适配器里按请求附加，避免空密钥绑死在 Bean 上。
     */
    @Bean
    RestClient llmRestClient(LlmProperties llmProperties) {
        HttpClient.Builder httpBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(llmProperties.timeoutSeconds()));
        if (llmProperties.proxyHost() != null && !llmProperties.proxyHost().isBlank()) {
            ProxySelector proxy = ProxySelector.of(
                    new InetSocketAddress(llmProperties.proxyHost(), llmProperties.resolvedProxyPort())
            );
            httpBuilder.proxy(proxy);
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpBuilder.build());
        factory.setReadTimeout(Duration.ofSeconds(llmProperties.timeoutSeconds()));
        return RestClient.builder()
                .baseUrl(llmProperties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Embedding HTTP 客户端。Authorization 在适配器里按请求附加。
     */
    @Bean
    RestClient embeddingRestClient(EmbeddingProperties embeddingProperties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(embeddingProperties.resolvedTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(embeddingProperties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * OCR HTTP 客户端。Authorization 在适配器里按请求附加。
     */
    @Bean
    RestClient ocrRestClient(OcrProperties ocrProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(ocrProperties.resolvedTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(ocrProperties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
