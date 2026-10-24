package com.aicode.enterprise.application;

import com.aicode.enterprise.infrastructure.sso.SsoProperties;
import org.springframework.stereotype.Service;

/**
 * 登录路由（Week 21）：按 {@code sso.enabled} 选择身份源。
 *
 * <p>关闭时行为与第 4 周完全一致（本地校验 + 本地会话），因此既有环境升级后
 * 不改任何配置也不会被 SSO 影响。</p>
 */
@Service
public class LoginFacade {

    private final LoginUserUseCase loginUserUseCase;
    private final SsoLoginUseCase ssoLoginUseCase;
    private final SsoProperties properties;

    public LoginFacade(
            LoginUserUseCase loginUserUseCase,
            SsoLoginUseCase ssoLoginUseCase,
            SsoProperties properties
    ) {
        this.loginUserUseCase = loginUserUseCase;
        this.ssoLoginUseCase = ssoLoginUseCase;
        this.properties = properties;
    }

    /**
     * 登录。
     *
     * @param command 用户名 + 密码
     * @return 本地会话结果
     */
    public LoginUserOutcome login(LoginUserCommand command) {
        return properties.enabled()
                ? ssoLoginUseCase.login(command)
                : loginUserUseCase.login(command);
    }
}
