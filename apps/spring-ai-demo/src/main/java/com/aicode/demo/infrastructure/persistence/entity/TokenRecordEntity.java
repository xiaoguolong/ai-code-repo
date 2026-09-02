package com.aicode.demo.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 单次调用 Token 记录。与 chat_session 外键关联，便于按会话统计。
 */
@Entity
@Table(name = "token_record")
public class TokenRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TokenRecordEntity() {
    }

    public TokenRecordEntity(
            String sessionId,
            String model,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs,
            String status,
            String errorCode,
            Instant occurredAt
    ) {
        this.sessionId = sessionId;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.latencyMs = latencyMs;
        this.status = status;
        this.errorCode = errorCode;
        this.occurredAt = occurredAt;
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

    public int promptTokens() {
        return promptTokens;
    }

    public int completionTokens() {
        return completionTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public String status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
