package com.aicode.framework.platform.dto;

/**
 * 清理过期记忆响应（Week 23）。
 *
 * @param deleted 实际删除条数
 * @param limit   本次批量上限
 */
public record PlatformMemoryPurgeResponse(int deleted, int limit) {
}
