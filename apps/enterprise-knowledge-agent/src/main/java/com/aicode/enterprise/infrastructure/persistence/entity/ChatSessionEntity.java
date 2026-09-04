package com.aicode.enterprise.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 会话实体。归属用户与知识库，session_id 为业务键对外暴露。
 */
@FluentMybatis(table = "chat_session")
public class ChatSessionEntity extends RichEntity {

    /** 主键，数据库自增。 */
    @TableId("id")
    private Long id;

    /** 会话业务键，对外暴露。 */
    @TableField("session_id")
    private String sessionId;

    /** 归属用户主键。 */
    @TableField("user_id")
    private Long userId;

    /** 归属知识库主键。 */
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /** 会话标题，可空。 */
    @TableField("title")
    private String title;

    /** 创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    public ChatSessionEntity() {
    }

    public ChatSessionEntity(String sessionId, Long userId, Long knowledgeBaseId, String title, Instant createdAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.title = title;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
