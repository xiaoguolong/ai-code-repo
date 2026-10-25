package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.AuthenticationException;
import com.aicode.core.domain.port.AuthTokenPort;
import com.aicode.core.domain.port.PasswordHasher;
import com.aicode.framework.infrastructure.logging.TraceIds;
import com.aicode.framework.platform.domain.model.AuditAction;
import com.aicode.framework.platform.domain.model.AuditLogEntry;
import com.aicode.framework.platform.domain.model.AuditResult;
import com.aicode.framework.platform.domain.model.PlatformTenant;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.AuditLogPort;
import com.aicode.framework.platform.domain.port.PlatformTenantPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 平台认证用例：登录 / 身份校验 / 注销 / 当前用户。
 *
 * <p>Week 16：登录成功与失败各写一条审计（{@code LOGIN} / {@code LOGIN_FAILED}）。
 * 审计写失败只记 WARN，不影响登录结果——审计不能成为登录单点故障。</p>
 *
 * <p>Week 21：增加两件事——</p>
 * <ol>
 *   <li>{@link #verify(PlatformLoginCommand)}：只校验凭据并返回身份，**不建会话**，
 *       供 enterprise 做 SSO（避免每次 SSO 登录在平台堆积孤儿 session）；</li>
 *   <li>租户校验：租户停用（或不存在）时按凭据错误处理（401），对外不暴露「租户被停用」。</li>
 * </ol>
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
    private final PlatformTenantPort tenantPort;

    /**
     * 兼容 Week 13–20 的四参构造器：不校验租户（旧单测无租户表）。
     *
     * @param platformUserPort 用户端口
     * @param passwordHasher   密码哈希
     * @param authTokenPort    令牌端口
     * @param auditLogPort     审计端口
     */
    public PlatformAuthUseCase(
            PlatformUserPort platformUserPort,
            PasswordHasher passwordHasher,
            AuthTokenPort authTokenPort,
            AuditLogPort auditLogPort
    ) {
        this(platformUserPort, passwordHasher, authTokenPort, auditLogPort, PlatformTenantPort.ALL_ACTIVE);
    }

    /**
     * 完整构造器（Spring 注入点，必须显式 {@code @Autowired}：本类有两个构造器）。
     *
     * @param platformUserPort 用户端口
     * @param passwordHasher   密码哈希
     * @param authTokenPort    令牌端口
     * @param auditLogPort     审计端口
     * @param tenantPort       租户端口（停用校验）
     */
    @Autowired
    public PlatformAuthUseCase(
            PlatformUserPort platformUserPort,
            PasswordHasher passwordHasher,
            AuthTokenPort authTokenPort,
            AuditLogPort auditLogPort,
            PlatformTenantPort tenantPort
    ) {
        this.platformUserPort = platformUserPort;
        this.passwordHasher = passwordHasher;
        this.authTokenPort = authTokenPort;
        this.auditLogPort = auditLogPort;
        this.tenantPort = tenantPort;
    }

    /**
     * 校验凭据并建立登录态。
     *
     * @param command 登录命令（用户名 + 明文密码）
     * @return 登录结果（token、用户、角色、租户）
     * @throws AuthenticationException 用户名不存在、密码错误或租户停用
     */
    public PlatformLoginOutcome login(PlatformLoginCommand command) {
        PlatformUser user = authenticate(command);
        String token = authTokenPort.login(user.id());
        audit(AuditLogEntry.of(user.id(), AuditAction.LOGIN, AUTH_RESOURCE,
                AuditResult.SUCCESS, TraceIds.current(),
                "role=" + user.roleKey() + " tenant=" + user.tenantKey()));
        return new PlatformLoginOutcome(token, user.id(), user.username(), user.roleKey(), user.tenantKey());
    }

    /**
     * 只校验凭据并返回身份（SSO 用，**不建会话**）。
     *
     * @param command 凭据
     * @return 平台用户（含租户）
     * @throws AuthenticationException 凭据错误或租户停用
     */
    public PlatformUser verify(PlatformLoginCommand command) {
        PlatformUser user = authenticate(command);
        audit(AuditLogEntry.of(user.id(), AuditAction.LOGIN, AUTH_RESOURCE,
                AuditResult.SUCCESS, TraceIds.current(),
                "sso=true role=" + user.roleKey() + " tenant=" + user.tenantKey()));
        return user;
    }

    /**
     * 注销当前登录态。
     */
    public void logout() {
        authTokenPort.logout();
    }

    /**
     * 查询用户（供内部使用）。
     *
     * @param userId 登录用户 ID
     * @return 平台用户
     * @throws AuthenticationException 用户不存在
     */
    public PlatformUser getUser(long userId) {
        return platformUserPort.findById(userId)
                .orElseThrow(() -> new AuthenticationException("invalid credentials"));
    }

    /**
     * 查询当前用户视图（含租户展示名，供 `/auth/me`）。
     *
     * @param userId 登录用户 ID
     * @return 用户视图
     * @throws AuthenticationException 用户不存在
     */
    public PlatformMeView me(long userId) {
        PlatformUser user = getUser(userId);
        if (user.disabled()) {
            // 防御：即使会话没被踢干净，也不让停用用户继续读 /auth/me
            throw new AuthenticationException("invalid credentials");
        }
        String tenantName = tenantPort.findByKey(user.tenantKey())
                .map(PlatformTenant::displayName)
                .orElse(user.tenantKey());
        return new PlatformMeView(user.id(), user.username(), user.roleKey(), user.tenantKey(), tenantName);
    }

    private PlatformUser authenticate(PlatformLoginCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String password = command.password() == null ? "" : command.password();
        PlatformUser user = platformUserPort.findByUsername(username).orElse(null);
        if (user == null || !passwordHasher.matches(password, username, user.passwordHash())) {
            // detail 只记用户名，绝不记密码；traceId 取自当前请求 MDC，便于与访问日志串联
            audit(AuditLogEntry.of(0L, AuditAction.LOGIN_FAILED, AUTH_RESOURCE,
                    AuditResult.FAILURE, TraceIds.current(), "username=" + username));
            throw new AuthenticationException("invalid credentials");
        }
        if (user.disabled()) {
            // 停用按「凭据无效」处理：不暴露账号状态，避免离职账号被探测
            audit(AuditLogEntry.of(0L, AuditAction.LOGIN_FAILED, AUTH_RESOURCE,
                    AuditResult.FAILURE, TraceIds.current(),
                    "username=" + username + " disabled=true"));
            throw new AuthenticationException("invalid credentials");
        }
        if (!tenantPort.isActive(user.tenantKey())) {
            // 停用租户按「凭据无效」处理：不暴露租户状态，避免探测
            audit(AuditLogEntry.of(0L, AuditAction.LOGIN_FAILED, AUTH_RESOURCE,
                    AuditResult.FAILURE, TraceIds.current(),
                    "username=" + username + " tenant=" + user.tenantKey() + " tenantInactive=true"));
            throw new AuthenticationException("invalid credentials");
        }
        return user;
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
