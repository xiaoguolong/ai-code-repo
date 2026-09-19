package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.User;
import com.aicode.enterprise.domain.port.UserPort;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 注册用户用例：校验 → 判重 → 哈希密码 → 落库。
 */
@Service
public class RegisterUserUseCase {

    private final UserPort userPort;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public RegisterUserUseCase(UserPort userPort, PasswordHasher passwordHasher, Clock clock) {
        this.userPort = userPort;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    /**
     * @throws InvalidChatRequestException 用户名或密码空白
     * @throws ConflictException           用户名已存在
     */
    public RegisterUserOutcome register(RegisterUserCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String password = command.password() == null ? "" : command.password();
        if (username.isEmpty()) {
            throw new InvalidChatRequestException("username must not be blank");
        }
        if (password.isEmpty()) {
            throw new InvalidChatRequestException("password must not be blank");
        }
        if (userPort.findByUsername(username).isPresent()) {
            throw new ConflictException("username already exists");
        }
        User created = userPort.create(new User(null, username, passwordHasher.hash(password, username), clock.instant()));
        return new RegisterUserOutcome(created.id(), created.username());
    }
}
