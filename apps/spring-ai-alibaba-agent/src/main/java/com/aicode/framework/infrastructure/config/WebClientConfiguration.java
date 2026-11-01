package com.aicode.framework.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Spring AI 流式所用的 {@link WebClient.Builder}（Week 31）。
 *
 * <p>真机问题：Jar 里没有 reactor-netty 时，Spring 的 WebClient 会退回 JDK HttpClient 连接器，
 * 对 {@code text/event-stream} 的聚合行为导致"整段响应到达后才吐出 chunk"（首字=总耗时）。
 * 这里显式使用 {@link ReactorClientHttpConnector}（Netty），保证逐块下发。</p>
 *
 * <p>Spring AI 的 OpenAI 自动装配会取容器里的 {@code WebClient.Builder}，因此覆盖此 bean 即可生效。</p>
 */
@Configuration
public class WebClientConfiguration {

    /** 模型响应最长等待：患者分析单次生成可达 60s，留足余量。 */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(180);

    /**
     * 用 Reactor Netty 连接器的 WebClient.Builder；baseUrl/鉴权仍由 Spring AI 设置。
     *
     * @return WebClient 构建器
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(RESPONSE_TIMEOUT);
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
