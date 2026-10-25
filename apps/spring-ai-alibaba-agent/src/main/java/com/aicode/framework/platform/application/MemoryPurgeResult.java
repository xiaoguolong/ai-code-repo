package com.aicode.framework.platform.application;

/**
 * 过期记忆清理结果（Week 23）。
 *
 * @param deleted 实际删除条数
 * @param limit   本次生效的批量上限
 */
public record MemoryPurgeResult(int deleted, int limit) {
}
