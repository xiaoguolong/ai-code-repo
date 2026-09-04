package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.AuthenticationException;
import com.aicode.enterprise.domain.port.PasswordHasher;
import com.aicode.enterprise.domain.model.User;
import com.aicode.enterprise.domain.port.AuthTokenPort;
import com.aicode.enterprise.domain.port.UserPort;
import org.springframework.stereotype.Service;

/**
 * 登录用例：校验凭据 → 建立登录态 → 返回令牌。
 */
@Service
public class LoginUserUseCase {

    private final UserPort userPort;
    private final PasswordHasher passwordHasher;
    private final AuthTokenPort authTokenPort;

    public LoginUserUseCase(UserPort userPort, PasswordHasher passwordHasher, AuthTokenPort authTokenPort) {
        this.userPort = userPort;
        this.passwordHasher = passwordHasher;
        this.authTokenPort = authTokenPort;
    }

    /**
     * @throws AuthenticationException 用户名不存在或密码错误
     */
    public LoginUserOutcome login(LoginUserCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String password = command.password() == null ? "" : command.password();
        User user = userPort.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("invalid credentials"));
        if (!passwordHasher.matches(password, username, user.passwordHash())) {
            throw new AuthenticationException("invalid credentials");
        }
        String token = authTokenPort.login(user.id());
        return new LoginUserOutcome(token, user.id(), user.username());
    }
}
