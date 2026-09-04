package com.aicode.enterprise.domain.model;

/**
 * 聊天消息角色。必须与模型协议的 role 分离传递，禁止把用户内容写入系统提示。
 */
public enum MessageRole {

    /** 系统提示，约束模型行为。 */
    SYSTEM("system"),

    /** 用户输入，一律当数据而非指令拼接。 */
    USER("user"),

    /** 模型回复。 */
    ASSISTANT("assistant");

    private final String apiValue;

    MessageRole(String apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * OpenAI 兼容协议中的 role 字符串。
     */
    public String apiValue() {
        return apiValue;
    }
}
