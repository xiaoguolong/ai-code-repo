package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 平台认证用例：登录 / 注销 / 当前用户。
 *
 * <p>Week 16：登录成功与失败各写一条审计（{@code LOGIN} / {@code LOGIN_FAILED}）。
 * 审计写失败只记 WARN，不影响登录结果——审计不能成为登录单点故障。</p>
 */
@Service
public class PlatformAuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuthUseCase.class);

    /** 审计资源标识：认证接口。 */
    private static final String AUTH_RESOURCE = "platform.auth";

    private final PlatformUserPort platformUserPort;
    private final PasswordHasher passwordHasher;
    private final AuthTokenPort authTokenPort;
    private final AuditLogPort auditLogPort;

    public PlatformAuthUseCase(
            PlatformUserPort platformUserPort,
            PasswordHasher passwordHasher,
            AuthTokenPort authTokenPort,
            AuditLogPort auditLogPort
    ) {
        this.platformUserPort = platformUserPort;
        this.passwordHasher = passwordHasher;
        this.authTokenPort = authTokenPort;
        this.auditLogPort = auditLogPort;
    }

    /**
     * 校验凭据并建立登录态。
     *
     * @param command 登录命令（用户名 + 明文密码）
     * @return 登录结果（token、用户、角色）
     * @throws AuthenticationException 用户名不存在或密码错误
     */
    public PlatformLoginOutcome login(PlatformLoginCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String password = command.password() == null ? "" : command.password();
        PlatformUser user = platformUserPort.findByUsername(username).orElse(null);
        if (user == null || !passwordHasher.matches(password, username, user.passwordHash())) {
            // detail 只记用户名，绝不记密码；traceId 取自当前请求 MDC，便于与访问日志串联
            audit(AuditLogEntry.of(0L, AuditAction.LOGIN_FAILED, AUTH_RESOURCE,
                    AuditResult.FAILURE, TraceIds.current(), "username=" + username));
            throw new AuthenticationException("invalid credentials");
        }
        String token = authTokenPort.login(user.id());
        audit(AuditLogEntry.of(user.id(), AuditAction.LOGIN, AUTH_RESOURCE,
                AuditResult.SUCCESS, TraceIds.current(), "role=" + user.roleKey()));
        return new PlatformLoginOutcome(token, user.id(), user.username(), user.roleKey());
    }

    /**
     * 注销当前登录态。
     */
    public void logout() {
        authTokenPort.logout();
    }

    /**
     * 查询用户（供 /me）。
     *
     * @param userId 登录用户 ID
     * @return 平台用户
     * @throws AuthenticationException 用户不存在
     */
    public PlatformUser getUser(long userId) {
        return platformUserPort.findById(userId)
                .orElseThrow(() -> new AuthenticationException("invalid credentials"));
    }

    /** 审计兜底：审计故障只告警，绝不影响登录主流程。 */
    private void audit(AuditLogEntry entry) {
        try {
            auditLogPort.record(entry);
        } catch (RuntimeException ex) {
            log.warn("[platform] audit record failed action={} userId={} error={}",
                    entry.action(), entry.userId(), ex.getMessage());
        }
    }
}
