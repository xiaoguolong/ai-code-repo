package com.aicode.framework.platform.domain.port;

/**
 * 出站端口：登录失败计数（Week 22）。
 *
 * <p>实现可以是内存滑动窗口（当前）或 Redis（多实例时）。key 由调用方拼装
 * （{@code ip|username}），端口本身不解析业务语义。</p>
 *
 * <p>约定：所有方法对空白 key 一律安全 no-op/返回 false，不允许抛异常——
 * 限流器故障绝不能变成登录不可用。</p>
 */
public interface LoginAttemptPort {

    /**
     * 该 key 当前是否处于封禁中。
     *
     * @param key 计数键（{@code ip|username}）
     * @return 封禁中返回 true
     */
    boolean isBlocked(String key);

    /**
     * 记一次失败。
     *
     * @param key 计数键
     */
    void recordFailure(String key);

    /**
     * 清零（登录/校验成功时）。
     *
     * @param key 计数键
     */
    void clear(String key);
}
