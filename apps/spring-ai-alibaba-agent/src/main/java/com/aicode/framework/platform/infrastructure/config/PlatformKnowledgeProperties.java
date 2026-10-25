package com.aicode.framework.platform.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库包装配置（Week 25，{@code platform.knowledge.*}）。
 *
 * <p>平台不存知识库正文：检索通过 enterprise 的内部只读端点完成。
 * 令牌必须与 enterprise 的 {@code ENTERPRISE_INTERNAL_TOKEN} 一致；未配置时检索会显式失败
 * （不降级成“无知识库也能答”，避免医疗场景无依据作答）。</p>
 */
@ConfigurationProperties(prefix = "platform.knowledge")
public class PlatformKnowledgeProperties {

    /** 是否启用知识库能力。 */
    private boolean enabled = true;
    /** enterprise 基地址（含端口，不含路径）。 */
    private String baseUrl = "http://127.0.0.1:8081";
    /** 内部共享令牌（与 enterprise 一致）。 */
    private String internalToken = "";
    /** 连接/读取超时（毫秒）。 */
    private int timeoutMs = 5000;
    /** 默认检索条数。 */
    private int defaultTopK = 4;
    /** 最大检索条数。 */
    private int maxTopK = 10;
    /** 问题长度上限。 */
    private int maxQuestionChars = 4000;

    /** @return 是否配置了内部令牌 */
    public boolean configured() {
        return internalToken != null && !internalToken.isBlank();
    }

    /** @return 生效的检索条数（默认值夹到 1..maxTopK） */
    public int resolvedDefaultTopK() {
        int max = resolvedMaxTopK();
        int topK = defaultTopK > 0 ? defaultTopK : 4;
        return Math.min(topK, max);
    }

    /** @return 生效的最大检索条数（1..50） */
    public int resolvedMaxTopK() {
        int max = maxTopK > 0 ? maxTopK : 10;
        return Math.min(max, 50);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public int getMaxTopK() {
        return maxTopK;
    }

    public void setMaxTopK(int maxTopK) {
        this.maxTopK = maxTopK;
    }

    public int getMaxQuestionChars() {
        return maxQuestionChars;
    }

    public void setMaxQuestionChars(int maxQuestionChars) {
        this.maxQuestionChars = maxQuestionChars;
    }
}
