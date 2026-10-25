package com.aicode.framework.platform.dto;

/**
 * 删除记忆响应（Week 23）。
 *
 * @param memoryId 被删除的记忆 ID
 * @param deleted  删除条数（成功恒为 1）
 */
public record PlatformMemoryDeleteResponse(String memoryId, int deleted) {
}
