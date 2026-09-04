package com.aicode.enterprise.infrastructure.security;

import com.aicode.enterprise.infrastructure.config.AuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sa-Token 加盐 md5 密码哈希单元测试。
 */
class SaTokenMd5PasswordHasherTest {

    private final SaTokenMd5PasswordHasher hasher = new SaTokenMd5PasswordHasher(new AuthProperties("test-salt"));

    @Test
    void hashesDeterministically() {
        assertThat(hasher.hash("pass123", "alice")).isEqualTo(hasher.hash("pass123", "alice"));
    }

    @Test
    void differentUsernamesProduceDifferentHashes() {
        assertThat(hasher.hash("pass123", "alice")).isNotEqualTo(hasher.hash("pass123", "bob"));
    }

    @Test
    void hashIsNotPlainPassword() {
        assertThat(hasher.hash("pass123", "alice")).isNotEqualTo("pass123");
    }

    @Test
    void matchesCorrectPassword() {
        String hashed = hasher.hash("pass123", "alice");
        assertThat(hasher.matches("pass123", "alice", hashed)).isTrue();
        assertThat(hasher.matches("wrong", "alice", hashed)).isFalse();
    }
}
