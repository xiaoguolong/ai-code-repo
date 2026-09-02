package com.aicode.demo.infrastructure.persistence.repository;

import com.aicode.demo.infrastructure.persistence.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 会话仓储。
 */
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {

    Optional<ChatSessionEntity> findBySessionId(String sessionId);
}
