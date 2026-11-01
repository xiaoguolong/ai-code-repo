package com.aicode.framework.platform.application;

/**
 * 流式增量脱敏（Week 31）。
 *
 * <p>直接对每个 delta 做 PII 掩码会在"手机号/身份证被切成两段"时漏掩：
 * 第一段末尾的 8 位数字还没凑够 11 位，正则匹配不到，就被发出去了。</p>
 *
 * <p>做法：保留末尾 {@value #CARRY_CHARS} 个字符不发（大于最长 PII 模式 18 位身份证），
 * 只把"安全前缀"脱敏后发出去；流结束时把尾巴一次性 flush。这样跨块的号码也能被正确掩码，
 * 代价是可见文本比模型晚 {@value #CARRY_CHARS} 个字符（首字仍在毫秒级，肉眼无感）。</p>
 *
 * <p>残留风险：邮箱 local part 超过 29 字符且正好跨块时，前缀 2 个字符可能漏掩；
 * 已在接口文档登记，可调大 carry 缓解。</p>
 */
final class StreamingTextSanitizer {

    /** 尾部保留字符数。 */
    static final int CARRY_CHARS = 32;

    private final PlatformGuardrailService guardrailService;
    private final long userId;
    private final String agentKey;
    private final StringBuilder pending = new StringBuilder();

    StreamingTextSanitizer(PlatformGuardrailService guardrailService, long userId, String agentKey) {
        this.guardrailService = guardrailService;
        this.userId = userId;
        this.agentKey = agentKey;
    }

    /**
     * 吃进一段模型增量，返回可以安全下发的文本（可能为空串）。
     *
     * @param delta 模型增量
     * @return 已脱敏的可下发文本
     */
    String accept(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        pending.append(delta);
        int safeLength = pending.length() - CARRY_CHARS;
        if (safeLength <= 0) {
            return "";
        }
        String safe = pending.substring(0, safeLength);
        pending.delete(0, safeLength);
        return guardrailService.sanitizeStreamText(userId, agentKey, safe);
    }

    /**
     * 流结束：把尾部剩余字符脱敏后下发。
     *
     * @return 已脱敏的尾部文本（可能为空串）
     */
    String flush() {
        if (pending.length() == 0) {
            return "";
        }
        String tail = pending.toString();
        pending.setLength(0);
        return guardrailService.sanitizeStreamText(userId, agentKey, tail);
    }
}
