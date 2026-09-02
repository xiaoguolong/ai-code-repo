package com.aicode.demo.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 会话实体。session_id 为业务键，对外暴露。
 */
@Entity
@Table(name = "chat_session")
public class ChatSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatSessionEntity() {
    }

    public ChatSessionEntity(String sessionId, String model, Instant createdAt) {
        this.sessionId = sessionId;
        this.model = model;
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public String sessionId() {
        return sessionId;
    }

    public String model() {
        return model;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
