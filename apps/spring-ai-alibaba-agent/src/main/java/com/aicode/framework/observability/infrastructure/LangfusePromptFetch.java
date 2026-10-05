package com.aicode.framework.observability.infrastructure;

import java.util.Optional;

/**
 * Prompt 拉取结果（Week 18）。
 *
 * <p>为什么要带 {@code outcome}：`拉不到` 有两种完全不同的运维含义 ——
 * {@code miss}（404：Prompt 还没在 Langfuse 里创建，去建即可）与
 * {@code fallback}（网络/服务异常：可观测设施出问题）。两者都回退本地模板，
 * 但指标分开计数，排障时一眼能分清「没配」和「连不上」。</p>
 *
 * @param payload 命中时的 Prompt；未命中为空
 * @param outcome 结果：{@code hit} / {@code miss} / {@code fallback}
 */
public record LangfusePromptFetch(Optional<LangfusePromptPayload> payload, String outcome) {

    /** 命中。 */
    public static LangfusePromptFetch hit(LangfusePromptPayload payload) {
        return new LangfusePromptFetch(Optional.of(payload), LangfusePromptClient.OUTCOME_HIT);
    }

    /** 未找到（404）：Prompt 未创建。 */
    public static LangfusePromptFetch miss() {
        return new LangfusePromptFetch(Optional.empty(), LangfusePromptClient.OUTCOME_MISS);
    }

    /** 回退：网络 / 服务 / 响应格式异常。 */
    public static LangfusePromptFetch fallback() {
        return new LangfusePromptFetch(Optional.empty(), LangfusePromptClient.OUTCOME_FALLBACK);
    }

    /** 是否命中。 */
    public boolean present() {
        return payload.isPresent();
    }
}
