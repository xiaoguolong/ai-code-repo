package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户持久化适配器（H2 仓储测试）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MyBatisUserAdapterTest {

    @Autowired
    private MyBatisUserAdapter adapter;

    @Test
    void createsAndFindsUserById() {
        User created = adapter.create(new User(null, "alice", "hashed", Instant.parse("2026-09-04T00:00:00Z")));

        assertThat(created.id()).isNotNull();
        Optional<User> found = adapter.findByUsername("alice");
        assertThat(found).isPresent();
        assertThat(found.get().passwordHash()).isEqualTo("hashed");
    }

    @Test
    void returnsEmptyForUnknownUsername() {
        assertThat(adapter.findByUsername("nobody")).isEmpty();
    }
}
