package com.aicode.patient.infrastructure.config;

import com.aicode.patient.application.AgentRuntimeConfig;
import com.aicode.patient.domain.ReActAgent;
import com.aicode.patient.domain.ReActOutputParser;
import com.aicode.patient.domain.port.ChatModelPort;
import com.aicode.patient.domain.port.PromptTemplatePort;
import com.aicode.patient.infrastructure.llm.OpenAiChatResponseMapper;
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
 * 基础设施装配：RestClient、运行时配置、领域服务。密钥不在此打印。
 */
@Configuration
@EnableConfigurationProperties({LlmProperties.class, AgentProperties.class})
public class AppConfiguration {

    /**
     * 把 LLM + Agent 配置映射为用例/领域所需的运行时参数。
     */
    @Bean
    AgentRuntimeConfig agentRuntimeConfig(LlmProperties llmProperties, AgentProperties agentProperties) {
        return new AgentRuntimeConfig(
                llmProperties.model(),
                llmProperties.temperature(),
                llmProperties.maxTokens(),
                agentProperties.maxIterations()
        );
    }

    /**
     * ReAct 输出解析器（纯函数，无依赖）。
     */
    @Bean
    ReActOutputParser reActOutputParser() {
        return new ReActOutputParser();
    }

    /**
     * ReAct 循环编排（领域服务）。依赖模型端口、Prompt 端口与解析器。
     */
    @Bean
    ReActAgent reActAgent(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ReActOutputParser parser,
            AgentRuntimeConfig config
    ) {
        return new ReActAgent(chatModelPort, promptTemplatePort, parser, config);
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
}
