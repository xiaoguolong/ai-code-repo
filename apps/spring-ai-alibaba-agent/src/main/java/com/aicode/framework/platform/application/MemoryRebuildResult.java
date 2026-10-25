package com.aicode.framework.platform.application;

/**
 * 长期记忆向量重建结果（Week 23）。
 *
 * @param scanned      本批扫描到的候选数
 * @param rebuilt      成功重建数
 * @param failed       失败数（embedding 异常或空向量）
 * @param currentModel 当前向量来源标识
 */
public record MemoryRebuildResult(int scanned, int rebuilt, int failed, String currentModel) {
}
