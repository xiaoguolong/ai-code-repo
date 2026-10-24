package com.aicode.framework.platform.infrastructure.security;

import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.PlatformRunScopePort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 从平台运行上下文推导记忆范围（Week 21）。
 *
 * <p>取值有两个来源，缺一不可：</p>
 * <ol>
 *   <li>用户 ID —— 来自 {@link PlatformSecurityContext}（由 {@code PlatformExecutionUseCase} 在调度前写入）；</li>
 *   <li>租户键 —— 来自 {@code platform_user} 记录。**绝不接受请求参数**，否则越权只需改一个字段。</li>
 * </ol>
 */
@Component
public class SecurityContextRunScopeAdapter implements PlatformRunScopePort {

    private final PlatformUserPort userPort;

    public SecurityContextRunScopeAdapter(PlatformUserPort userPort) {
        this.userPort = userPort;
    }

    @Override
    public Optional<PlatformMemoryScope> currentScope() {
        Long userId = PlatformSecurityContext.currentPlatformUserId();
        if (userId == null) {
            return Optional.empty();
        }
        return userPort.findById(userId).map(PlatformMemoryScope::of);
    }
}
