package com.aicode.gateway.infrastructure.redis;

import com.aicode.gateway.domain.model.GatewaySession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 网关会话编解码（Week 19）。
 *
 * <p>手写 JSON 而不是直接序列化 record：会话内容将来会随需求增删字段，
 * 手写编解码可以<b>容忍旧格式</b>（缺字段按默认值），避免一次发布让所有在线会话失效。</p>
 *
 * <p>不写入任何日志，值里含平台 token（凭据），禁止打印。</p>
 */
@Component
public class GatewaySessionCodec {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper JSON 序列化器
     */
    public GatewaySessionCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 会话 → JSON。
     *
     * @param session 会话
     * @return JSON 文本
     * @throws IllegalStateException 序列化失败（不应发生；由适配器兜底）
     */
    public String encode(GatewaySession session) {
        try {
            return objectMapper.writeValueAsString(new SessionJson(
                    session.platformToken(), session.userId(), session.username(),
                    session.roleKey(), session.expiresAtEpochSecond()));
        } catch (Exception ex) {
            throw new IllegalStateException("网关会话序列化失败", ex);
        }
    }

    /**
     * JSON → 会话。
     *
     * @param json JSON 文本
     * @return 会话；内容为空或损坏时返回 null（调用方视为未登录）
     */
    public GatewaySession decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            SessionJson parsed = objectMapper.readValue(json, SessionJson.class);
            if (parsed.platformToken() == null || parsed.platformToken().isBlank()) {
                return null;
            }
            return new GatewaySession(
                    parsed.platformToken(), parsed.userId(),
                    parsed.username() == null ? "" : parsed.username(),
                    parsed.roleKey() == null ? "" : parsed.roleKey(),
                    parsed.expiresAtEpochSecond());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 存储形态（字段名即 JSON 键名，改动需考虑兼容）。
     *
     * @param platformToken        平台 token
     * @param userId               用户 ID
     * @param username             登录名
     * @param roleKey              角色标识
     * @param expiresAtEpochSecond 过期时间（Unix 秒）
     */
    public record SessionJson(
            String platformToken, long userId, String username, String roleKey, long expiresAtEpochSecond) {
    }
}
