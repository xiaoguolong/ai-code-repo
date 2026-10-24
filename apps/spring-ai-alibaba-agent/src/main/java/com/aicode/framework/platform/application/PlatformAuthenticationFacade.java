package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.framework.platform.domain.exception.PlatformRateLimitExceededException;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.LoginAttemptPort;
import com.aicode.framework.platform.infrastructure.config.PlatformRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 登录/身份校验门面（Week 22）：先限流、再认证、成功清零。
 *
 * <p>为什么放在应用层而不是过滤器：</p>
 * <ul>
 *   <li>过滤器读请求体需要缓存包装，容易把 JSON 消费掉；</li>
 *   <li>放在这里可以用固定 {@code Clock} 纯单测窗口与封禁到期，不需要起 Web 环境；</li>
 *   <li>控制器只负责「取客户端 IP」这一件协议相关的事。</li>
 * </ul>
 *
 * <p>顺序很关键：<b>被限流时绝不查库、不比对密码</b>，否则限流只是把攻击成本从「一次哈希」变成「两次查询」。</p>
 */
@Service
public class PlatformAuthenticationFacade {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuthenticationFacade.class);

    private static final String UNKNOWN_IP = "unknown";

    private final PlatformAuthUseCase authUseCase;
    private final LoginAttemptPort loginAttemptPort;
    private final PlatformRateLimitProperties properties;

    public PlatformAuthenticationFacade(
            PlatformAuthUseCase authUseCase,
            LoginAttemptPort loginAttemptPort,
            PlatformRateLimitProperties properties
    ) {
        this.authUseCase = authUseCase;
        this.loginAttemptPort = loginAttemptPort;
        this.properties = properties;
    }

    /**
     * 登录（建会话）。
     *
     * @param command  凭据
     * @param clientIp 客户端 IP（由控制器解析）
     * @return 登录结果
     * @throws PlatformRateLimitExceededException 该 IP+用户名 已被限流
     * @throws AuthenticationException            凭据错误（计入失败次数）
     */
    public PlatformLoginOutcome login(PlatformLoginCommand command, String clientIp) {
        return execute(command, clientIp, () -> authUseCase.login(command));
    }

    /**
     * 身份校验（不建会话，供 SSO 使用）。
     *
     * @param command  凭据
     * @param clientIp 客户端 IP
     * @return 平台用户
     * @throws PlatformRateLimitExceededException 已被限流
     * @throws AuthenticationException            凭据错误
     */
    public PlatformUser verify(PlatformLoginCommand command, String clientIp) {
        return execute(command, clientIp, () -> authUseCase.verify(command));
    }

    private <T> T execute(PlatformLoginCommand command, String clientIp, AuthAction<T> action) {
        if (!properties.enabled()) {
            return action.run();
        }
        String key = attemptKey(clientIp, command == null ? null : command.username());
        if (loginAttemptPort.isBlocked(key)) {
            log.warn("[platform] auth rate limited key={}", key);
            throw new PlatformRateLimitExceededException("too many authentication attempts");
        }
        try {
            T result = action.run();
            loginAttemptPort.clear(key);
            return result;
        } catch (AuthenticationException ex) {
            loginAttemptPort.recordFailure(key);
            throw ex;
        }
    }

    private String attemptKey(String clientIp, String username) {
        String ip = (clientIp == null || clientIp.isBlank()) ? UNKNOWN_IP : clientIp.trim();
        String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return ip + "|" + user;
    }

    /** 认证动作（只为让 login / verify 共用同一段限流逻辑）。 */
    @FunctionalInterface
    private interface AuthAction<T> {
        T run();
    }
}
