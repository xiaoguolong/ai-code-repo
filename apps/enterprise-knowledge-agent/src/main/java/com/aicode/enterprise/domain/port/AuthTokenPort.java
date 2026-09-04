package com.aicode.enterprise.domain.port;

/**
 * 出站端口：登录令牌。抽象掉 Sa-Token，使应用层不依赖鉴权框架。
 */
public interface AuthTokenPort {

    /**
     * 为指定用户建立登录态，返回令牌值。
     *
     * @param userId 用户主键
     * @return 令牌字符串
     */
    String login(long userId);

    /**
     * 注销当前登录态。
     */
    void logout();
}
