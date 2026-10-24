package com.aicode.framework.platform.domain.model;

/**
 * 记忆命中项（Week 21）。
 *
 * @param record 命中记录
 * @param score  余弦相似度；按时间列表返回时为 0（无查询向量）
 */
public record PlatformMemoryHit(PlatformMemoryRecord record, double score) {
}
