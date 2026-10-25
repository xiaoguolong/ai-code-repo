package com.aicode.framework.platform.dto;

/**
 * 重建记忆向量响应（Week 23）。
 *
 * @param scanned      本批扫描到的候选数
 * @param rebuilt      成功重建数
 * @param failed       失败数
 * @param currentModel 当前向量来源标识
 */
public record PlatformMemoryRebuildResponse(int scanned, int rebuilt, int failed, String currentModel) {
}
