package com.aicode.demo.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 会话实体。session_id 为业务键，对外暴露。
 */
@FluentMybatis(table = "chat_session")
public class ChatSessionEntity extends RichEntity {

    @TableId("id")
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("model")
    private String model;

    @TableField("created_at")
    private Instant createdAt;

    public ChatSessionEntity() {
    }

    public ChatSessionEntity(String sessionId, String model, Instant createdAt) {
        this.sessionId = sessionId;
        this.model = model;
        this.createdAt = createdAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
