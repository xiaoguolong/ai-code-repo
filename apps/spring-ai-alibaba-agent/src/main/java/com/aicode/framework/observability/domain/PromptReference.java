package com.aicode.framework.observability.domain;

/**
 * 一次生成所关联的 Langfuse Prompt 引用（Week 18）。
 *
 * <p>只有 {@code generation} 类型的观测才能挂 Prompt（Langfuse 的硬约束），
 * 因此该引用只出现在 {@code llm.chat} 观测上。</p>
 *
 * @param name    Prompt 名（与 Langfuse 中的 prompt name 一致）
 * @param version Langfuse 版本号（字符串形式的整数）
 */
public record PromptReference(String name, String version) {
}
