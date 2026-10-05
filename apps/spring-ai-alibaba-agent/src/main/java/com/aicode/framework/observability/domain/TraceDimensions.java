package com.aicode.framework.observability.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次请求的 Langfuse trace 维度（Week 18）。
 *
 * <p>为什么要有它：Langfuse v4 的过滤与聚合作用在<b>单个 observation</b> 上，官方要求
 * {@code userId} / {@code sessionId} / {@code traceName} / {@code tags} 出现在 trace 内每个 span 上，
 * 只写 root span 无法按这些维度检索。这些值在请求处理过程中才可知（登录态 + Run 入参），
 * 因此用一个请求期对象承载，由 {@code LangfuseSpanEnricher} 在 span 开始时写到 span 属性上。</p>
 *
 * @param userId    平台用户 ID，可为空
 * @param sessionId 会话 ID（本项目取 patientId），可为空
 * @param traceName trace 名，可为空
 * @param tags      标签，非 null（null 视为空列表）
 */
public record TraceDimensions(String userId, String sessionId, String traceName, List<String> tags) {

    public TraceDimensions {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /**
     * Agent Run 的维度：trace 名固定 {@code agent:<agentKey>}，标签为「Agent 类型 + Agent 标识」。
     *
     * @param userId    调用者用户 ID
     * @param agentKey  Agent 标识
     * @param agentType Agent 类型（枚举名）
     * @param sessionId 会话 ID（patientId），可为空
     * @return 维度对象
     */
    public static TraceDimensions agentRun(long userId, String agentKey, String agentType, String sessionId) {
        String key = agentKey == null ? "" : agentKey.trim();
        List<String> tags = new ArrayList<>();
        if (agentType != null && !agentType.isBlank()) {
            tags.add(agentType.trim());
        }
        if (!key.isEmpty()) {
            tags.add(key);
        }
        return new TraceDimensions(
                String.valueOf(userId),
                sessionId == null || sessionId.isBlank() ? null : sessionId.trim(),
                key.isEmpty() ? null : "agent:" + key,
                tags);
    }

    /** 是否没有任何可用维度（此时不必写入 trace 维度属性）。 */
    public boolean isEmpty() {
        return blank(userId) && blank(sessionId) && blank(traceName) && tags.isEmpty();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
