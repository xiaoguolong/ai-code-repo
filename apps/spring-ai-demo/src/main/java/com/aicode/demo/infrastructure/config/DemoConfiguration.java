package com.aicode.demo.infrastructure.config;

import com.aicode.demo.application.ChatRuntimeConfig;
import com.aicode.demo.infrastructure.llm.OpenAiChatResponseMapper;
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
 * 基础设施装配：时钟、RestClient、运行时配置。密钥不在此打印。
 */
@Configuration
@EnableConfigurationProperties({LlmProperties.class, ChatAppProperties.class})
public class DemoConfiguration {

    /**
     * UTC 时钟，便于单测注入 fixed Clock。
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 把配置映射为用例所需的运行时参数。
     */
    @Bean
    ChatRuntimeConfig chatRuntimeConfig(LlmProperties llmProperties, ChatAppProperties chatAppProperties) {
        return new ChatRuntimeConfig(
                llmProperties.model(),
                llmProperties.temperature(),
                llmProperties.maxTokens(),
                chatAppProperties.resolvedMaxMemoryMessages()
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
     * LLM HTTP 客户端。Authorization 在适配器里按请求附加，避免空密钥绑死在 Bean 上。
     * 配置了 proxyHost 时启用代理，解决 TLS 握手被本地代理/防火墙截断的问题。
     */
    @Bean
    RestClient llmRestClient(LlmProperties llmProperties) {
        HttpClient.Builder httpBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(llmProperties.timeoutSeconds()));
        if (llmProperties.proxyHost() != null && !llmProperties.proxyHost().isBlank()) {
            ProxySelector proxy = ProxySelector.of(
                    new InetSocketAddress(llmProperties.proxyHost(), llmProperties.proxyPort())
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
}
