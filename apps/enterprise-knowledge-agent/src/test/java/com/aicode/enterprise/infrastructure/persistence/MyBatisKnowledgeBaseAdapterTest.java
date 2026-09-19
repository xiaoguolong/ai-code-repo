package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.core.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库持久化适配器（H2 仓储测试）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MyBatisKnowledgeBaseAdapterTest {

    @Autowired
    private MyBatisKnowledgeBaseAdapter adapter;

    @Autowired
    private MyBatisUserAdapter userAdapter;

    @Test
    void createsWithIdAndListsByUser() {
        User user = userAdapter.create(new User(null, "bob", "hashed", Instant.parse("2026-09-04T00:00:00Z")));

        KnowledgeBase created = adapter.create(new KnowledgeBase(null, user.id(), "知识库", "描述", Instant.parse("2026-09-04T00:00:00Z")));

        assertThat(created.id()).isNotNull();
        assertThat(adapter.listByUser(user.id())).hasSize(1);
    }
}
