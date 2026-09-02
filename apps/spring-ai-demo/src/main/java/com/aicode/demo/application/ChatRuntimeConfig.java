package com.aicode.demo.application;

/**
 * 用例运行时配置。由基础设施把配置文件映射进来，领域不读 Environment。
 *
 * @param maxMemoryMessages 短期记忆条数上限（不含 system），默认 20
 */
public record ChatRuntimeConfig(String model, double temperature, int maxTokens, int maxMemoryMessages) {

    /**
     * 兼容第1周三参数构造，记忆窗口默认 20。
     */
    public ChatRuntimeConfig(String model, double temperature, int maxTokens) {
        this(model, temperature, maxTokens, 20);
    }
}
