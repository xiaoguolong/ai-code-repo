package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.User;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.enterprise.domain.exception.ConflictException;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.model.PlatformIdentity;
import com.aicode.enterprise.domain.port.PlatformIdentityPort;
import com.aicode.enterprise.domain.port.SsoUserLinkPort;
import com.aicode.enterprise.domain.port.UserPort;
import com.aicode.enterprise.infrastructure.sso.SsoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * SSO 登录用例（Week 21）：委托平台校验 → 建链/开户 → 建立**本地**会话。
 *
 * <p>三条硬规则：</p>
 * <ol>
 *   <li>不保存平台 token，也不复用平台会话（platform `/auth/verify` 本来就不建会话）；</li>
 *   <li>首次登录自动开户时写**随机密码**（用户无法用该密码在本地登录，避免绕过 SSO）；</li>
 *   <li>本地已有同名用户但未建链时返回 409，**不自动合并**——同名可能是两个人。</li>
 * </ol>
 */
@Service
public class SsoLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(SsoLoginUseCase.class);

    private final PlatformIdentityPort platformIdentityPort;
    private final SsoUserLinkPort ssoUserLinkPort;
    private final UserPort userPort;
    private final PasswordHasher passwordHasher;
    private final AuthTokenPort authTokenPort;
    private final SsoProperties properties;
    private final Clock clock;

    public SsoLoginUseCase(
            PlatformIdentityPort platformIdentityPort,
            SsoUserLinkPort ssoUserLinkPort,
            UserPort userPort,
            PasswordHasher passwordHasher,
            AuthTokenPort authTokenPort,
            SsoProperties properties,
            Clock clock
    ) {
        this.platformIdentityPort = platformIdentityPort;
        this.ssoUserLinkPort = ssoUserLinkPort;
        this.userPort = userPort;
        this.passwordHasher = passwordHasher;
        this.authTokenPort = authTokenPort;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 用平台身份登录 enterprise。
     *
     * @param command 用户名 + 密码
     * @return 本地会话令牌与本地用户
     * @throws InvalidChatRequestException 用户名/密码空白
     * @throws AuthenticationException     平台凭据错误
     * @throws ForbiddenException          未建链且关闭了自动开户
     * @throws ConflictException           本地同名用户已存在但未建链
     */
    public LoginUserOutcome login(LoginUserCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String password = command.password() == null ? "" : command.password();
        if (username.isEmpty() || password.isEmpty()) {
            throw new InvalidChatRequestException("username and password must not be blank");
        }

        PlatformIdentity identity = platformIdentityPort.authenticate(username, password);
        long enterpriseUserId = resolveEnterpriseUserId(identity);
        String token = authTokenPort.login(enterpriseUserId);
        log.info("[sso] login success platformUserId={} enterpriseUserId={} tenantKey={}",
                identity.userId(), enterpriseUserId, identity.tenantKey());
        return new LoginUserOutcome(token, enterpriseUserId, identity.username());
    }

    private long resolveEnterpriseUserId(PlatformIdentity identity) {
        Optional<Long> linked = ssoUserLinkPort.findEnterpriseUserId(identity.userId());
        if (linked.isPresent()) {
            return linked.get();
        }
        if (!properties.autoProvision()) {
            throw new ForbiddenException("sso user is not linked and auto provision is disabled");
        }
        if (userPort.findByUsername(identity.username()).isPresent()) {
            throw new ConflictException("username already exists locally without sso link");
        }
        User created = userPort.create(new User(
                null,
                identity.username(),
                passwordHasher.hash(UUID.randomUUID().toString(), identity.username()),
                clock.instant()));
        ssoUserLinkPort.link(identity.userId(), created.id(), identity.tenantKey());
        log.info("[sso] provisioned local user enterpriseUserId={} platformUserId={} tenantKey={}",
                created.id(), identity.userId(), identity.tenantKey());
        return created.id();
    }
}
