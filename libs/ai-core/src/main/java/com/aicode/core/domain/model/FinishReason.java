package com.aicode.core.domain.model;

import java.util.Locale;

/**
 * 模型补全结束原因。原生 Function Calling 依赖 {@link #TOOL_CALLS} 区分「要求调用工具」与「最终回答」。
 */
public enum FinishReason {

    /** 正常结束，content 即最终答案。 */
    STOP("stop"),

    /** 要求调用工具，本轮 content 为空，toolCalls 携带工具调用。 */
    TOOL_CALLS("tool_calls"),

    /** 达到长度上限被截断。 */
    LENGTH("length"),

    /** 未知或缺失。 */
    UNKNOWN("unknown");

    private final String apiValue;

    FinishReason(String apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * 从厂商响应中的 finish_reason 字符串解析。
     *
     * @param value 原始值，可为 null/空
     * @return 对应枚举；未知或空返回 {@link #UNKNOWN}
     */
    public static FinishReason fromApi(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (FinishReason reason : values()) {
            if (reason.apiValue.equals(normalized)) {
                return reason;
            }
        }
        return UNKNOWN;
    }
}
