package com.aicode.demo.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型接入配置。apiKey 只允许来自环境变量，禁止写入仓库。
 *
 * @param baseUrl         默认 https://api.deepseek.com
 * @param apiKey          对应 LLM_API_KEY
 * @param model           默认 deepseek-chat
 * @param maxTokens       生成上限，防止跑飞烧钱
 * @param temperature     采样温度
 * @param timeoutSeconds  读超时秒数
 * @param proxyHost       代理主机，空则不启用代理（对应 LLM_PROXY_HOST）
 * @param proxyPort       代理端口，可空；空时回退 7890
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String model,
        int maxTokens,
        double temperature,
        int timeoutSeconds,
        String proxyHost,
        Integer proxyPort
) {

    /**
     * @return 有效代理端口，缺省或空时回退 7890
     */
    public int resolvedProxyPort() {
        return proxyPort == null ? 7890 : proxyPort;
    }
}
