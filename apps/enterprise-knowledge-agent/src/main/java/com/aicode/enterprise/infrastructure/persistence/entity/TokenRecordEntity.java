package com.aicode.enterprise.infrastructure.persistence.entity;

import cn.org.atool.fluent.mybatis.annotation.FluentMybatis;
import cn.org.atool.fluent.mybatis.annotation.TableField;
import cn.org.atool.fluent.mybatis.annotation.TableId;
import cn.org.atool.fluent.mybatis.base.RichEntity;

import java.time.Instant;

/**
 * 单次调用 Token 记录。归属用户与会话，便于按用户统计成本。
 */
@FluentMybatis(table = "token_record")
public class TokenRecordEntity extends RichEntity {

    /** 主键，数据库自增。 */
    @TableId("id")
    private Long id;

    /** 归属会话业务键。 */
    @TableField("session_id")
    private String sessionId;

    /** 归属用户主键。 */
    @TableField("user_id")
    private Long userId;

    /** 模型名。 */
    @TableField("model")
    private String model;

    /** 提示 Token 数。 */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** 补全 Token 数。 */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** 总 Token 数。 */
    @TableField("total_tokens")
    private Integer totalTokens;

    /** 创建时间。 */
    @TableField("created_at")
    private Instant createdAt;

    public TokenRecordEntity() {
    }

    public TokenRecordEntity(
            String sessionId,
            Long userId,
            String model,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            Instant createdAt
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
