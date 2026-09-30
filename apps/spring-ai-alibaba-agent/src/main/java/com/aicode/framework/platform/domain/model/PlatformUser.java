package com.aicode.framework.platform.domain.model;

/**
 * 平台用户（与 enterprise 用户独立，Week 13 内存存储）。
 */
public record PlatformUser(
        long id,
        String username,
        String passwordHash,
        String roleKey
) {
}
