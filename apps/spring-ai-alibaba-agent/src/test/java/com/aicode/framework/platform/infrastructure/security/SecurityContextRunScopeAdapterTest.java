package com.aicode.framework.platform.infrastructure.security;

import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryPlatformUserAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行上下文 → 记忆 scope 的适配器：scope 只能由服务端推导，不接受外部参数。
 */
class SecurityContextRunScopeAdapterTest {

    @AfterEach
    void tearDown() {
        PlatformSecurityContext.clear();
    }

    @Test
    void derivesScopeFromCurrentRunUserAndTenant() {
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        users.save(new PlatformUser(2L, "operator", "hash", "operator", "hospital-b"));
        SecurityContextRunScopeAdapter adapter = new SecurityContextRunScopeAdapter(users);

        assertThat(adapter.currentScope()).isEmpty();

        PlatformSecurityContext.beginPlatformRun(2L);

        assertThat(adapter.currentScope()).contains(new PlatformMemoryScope("hospital-b", 2L));
    }

    @Test
    void returnsEmptyWhenUserDisappeared() {
        InMemoryPlatformUserAdapter users = new InMemoryPlatformUserAdapter();
        SecurityContextRunScopeAdapter adapter = new SecurityContextRunScopeAdapter(users);

        PlatformSecurityContext.beginPlatformRun(99L);

        assertThat(adapter.currentScope()).isEmpty();
    }
}
