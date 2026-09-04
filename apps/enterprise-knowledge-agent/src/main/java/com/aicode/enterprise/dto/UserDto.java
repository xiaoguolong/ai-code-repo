package com.aicode.enterprise.dto;

/**
 * 用户信息。不含密码哈希。
 */
public record UserDto(Long userId, String username) {
}
