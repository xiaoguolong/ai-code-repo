package com.aicode.gateway.domain.port;

import com.aicode.gateway.domain.model.GatewaySession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 网关联会话存储出站端口（Week 19）。
 *
 * <p>出参一律 {@link Mono}：网关是响应式应用，端口不得暴露阻塞式 API
 * （否则会在事件循环线程上做阻塞 I/O，把整个网关拖死）。</p>
 *
 * <p>失败约定：存储不可用由适配器抛出运行时异常，由全局异常处理映射为 500；
 * 端口不吞异常（「会话查不出来」与「没有会话」必须区分，前者不能当成未登录静默放行）。</p>
 */
public interface GatewaySessionPort {

    /**
     * 写入一个会话。
     *
     * @param sessionToken 网关联会话 token
     * @param session      会话内容
     * @param ttl          有效期
     * @return 写入完成信号
     */
    Mono<Void> save(String sessionToken, GatewaySession session, Duration ttl);

    /**
     * 读取会话。
     *
     * @param sessionToken 网关联会话 token
     * @return 会话；不存在时为 {@link Mono#empty()}（<b>不</b>返回 null 值）
     */
    Mono<GatewaySession> find(String sessionToken);

    /**
     * 删除会话（登出）。
     *
     * @param sessionToken 网关联会话 token
     * @return 删除完成信号；键不存在也视为成功
     */
    Mono<Void> remove(String sessionToken);

    /**
     * 列出当前全部会话（只读概况，用于自检与运维，不做权限用途）。
     *
     * @return 会话列表；无会话时为空列表
     */
    Mono<List<GatewaySession>> list();

    /**
     * 存储实现标识（自检展示用，如 {@code redis}）。
     *
     * @return 实现名
     */
    String storeName();
}
