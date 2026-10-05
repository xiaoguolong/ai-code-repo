package com.aicode.framework.observability.infrastructure;

/**
 * 从 Langfuse 取回的 Prompt（Week 18）。
 *
 * @param text    Prompt 文本（text 类型为原文；chat 类型取 system 消息内容）
 * @param version Langfuse 版本号（字符串形式的整数，用于在 trace 上关联版本）
 */
public record LangfusePromptPayload(String text, String version) {
}
