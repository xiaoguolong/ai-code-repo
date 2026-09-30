package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.springframework.stereotype.Service;

/**
 * 平台认证用例：登录 / 注销 / 当前用户。
 */
@Service
public class PlatformAuthUseCase {

    private final PlatformUserPort platformUserPort;
    private final PasswordHasher passwordHasher;
    private final AuthTokenPort authTokenPort;

    public PlatformAuthUseCase(
            PlatformUserPort platformUserPort,
            PasswordHasher passwordHasher,
            AuthTokenPort authTokenPort
    ) {
        this.platformUserPort = platformUserPort;
        this.passwordHasher = passwordHasher;
        this.authTokenPort = authTokenPort;
    }

    /** 校验凭据并建立登录态。 */
    public PlatformLoginOutcome login(PlatformLoginCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String password = command.password() == null ? "" : command.password();
        PlatformUser user = platformUserPort.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("invalid credentials"));
        if (!passwordHasher.matches(password, username, user.passwordHash())) {
            throw new AuthenticationException("invalid credentials");
        }
        String token = authTokenPort.login(user.id());
        return new PlatformLoginOutcome(token, user.id(), user.username(), user.roleKey());
    }

    /** 注销当前登录态。 */
    public void logout() {
        authTokenPort.logout();
    }

    /** 查询用户（供 /me）。 */
    public PlatformUser getUser(long userId) {
        return platformUserPort.findById(userId)
                .orElseThrow(() -> new AuthenticationException("invalid credentials"));
    }
}
