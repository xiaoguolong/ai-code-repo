package com.aicode.enterprise.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 单条消息实体。不保存 system 提示；只存 user/assistant。
 */
@FluentMybatis(table = "chat_message")
public class ChatMessageEntity extends RichEntity {

    /** 主键，数据库自增。 */
    @TableId("id")
    private Long id;

    /** 归属会话业务键。 */
    @TableField("session_id")
    private String sessionId;

    /** 消息角色：USER / ASSISTANT。 */
    @TableField("role")
    private String role;

    /** 消息正文。 */
    @TableField("content")
    private String content;

    /** 创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    public ChatMessageEntity() {
    }

    public ChatMessageEntity(String sessionId, String role, String content, Instant createdAt) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
