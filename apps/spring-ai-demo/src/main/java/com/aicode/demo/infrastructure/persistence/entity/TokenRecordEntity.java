package com.aicode.demo.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 单次调用 Token 记录。与 chat_session 外键关联，便于按会话统计。
 */
@FluentMybatis(table = "token_record")
public class TokenRecordEntity extends RichEntity {

    @TableId("id")
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("model")
    private String model;

    @TableField("prompt_tokens")
    private int promptTokens;

    @TableField("completion_tokens")
    private int completionTokens;

    @TableField("total_tokens")
    private int totalTokens;

    @TableField("latency_ms")
    private long latencyMs;

    @TableField("status")
    private String status;

    @TableField("error_code")
    private String errorCode;

    @TableField("occurred_at")
    private Instant occurredAt;

    public TokenRecordEntity() {
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
