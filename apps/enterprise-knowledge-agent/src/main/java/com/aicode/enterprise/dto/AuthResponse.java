package com.aicode.enterprise.dto;

/**
 * 登录成功体。
 */
public record AuthResponse(String token, UserDto user) {
}
