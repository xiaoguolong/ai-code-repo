package com.aicode.gateway.domain.model;

/**
 * 当前请求的调用者身份（由全局鉴权过滤器从会话解析后放入请求属性）。
 *
 * <p>只承载「换发上游 Authorization 所需的最小信息」，不含任何权限规则。</p>
 *
 * @param sessionToken  网关联会话 token
 * @param platformToken 平台 token（用于换发上游 Authorization，不对外）
 * @param userId        用户 ID
 * @param username      登录名
 */
public record SessionPrincipal(String sessionToken, String platformToken, long userId, String username) {

    /**
     * 由会话内容构造身份摘要。
     *
     * @param sessionToken 网关联会话 token
     * @param session      会话内容
     * @return 身份摘要
     */
    public static SessionPrincipal from(String sessionToken, GatewaySession session) {
        return new SessionPrincipal(
                sessionToken, session.platformToken(), session.userId(), session.username());
    }
}
