package com.aicode.core.infrastructure.config;

import com.aicode.core.domain.FileUrlAssembler;
import com.aicode.core.infrastructure.llm.OpenAiChatResponseMapper;
import com.aicode.core.infrastructure.ocr.PaddleOcrResponseMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * ai-core 基础设施装配：统一注册配置属性、JSON 映射器、URL 组装器与 LLM/Embedding/OCR HTTP 客户端。
 * 各 app 通过组件扫描 com.aicode.core 引入本装配；密钥不在此打印。
 */
@Configuration
@EnableConfigurationProperties({
        LlmProperties.class,
        EmbeddingProperties.class,
        RagProperties.class,
        OcrProperties.class,
        AuthProperties.class,
        FileProperties.class,
        PromptProperties.class
})
public class AiCoreConfiguration {

    /**
     * JSON 映射器，从厂商响应中提取 content / tool_calls 与 usage。
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
