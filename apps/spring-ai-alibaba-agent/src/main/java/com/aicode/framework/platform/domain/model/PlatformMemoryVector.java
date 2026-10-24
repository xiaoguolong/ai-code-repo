package com.aicode.framework.platform.domain.model;

/**
 * 记忆 + 向量（存储层内部模型）。
 *
 * <p>向量的存储形态属于基础设施细节（当前以 JSON 文本落库），因此只在适配器与排序函数之间传递，
 * 不出现在对外 DTO 中。</p>
 *
 * @param record    记忆记录
 * @param embedding 向量，可能为空数组（向量化失败时）
 */
public record PlatformMemoryVector(PlatformMemoryRecord record, float[] embedding) {
}
