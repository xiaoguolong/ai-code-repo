package com.aicode.enterprise.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 应用用户。passwordHash 为加盐哈希后的密码，禁止存明文。
 */
public record User(Long id, String username, String passwordHash, Instant createdAt) {

    public User {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
