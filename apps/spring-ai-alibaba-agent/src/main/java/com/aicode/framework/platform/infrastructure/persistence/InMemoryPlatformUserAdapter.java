package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内平台用户存储（Week 13 V1，Week 16 起仅在 {@code platform.persistence.mode=memory} 生效）。
 */
@Component
@ConditionalOnProperty(name = "platform.persistence.mode", havingValue = "memory")
public class InMemoryPlatformUserAdapter implements PlatformUserPort {

    private final ConcurrentHashMap<Long, PlatformUser> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlatformUser> byUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<PlatformUser> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byUsername.get(username.trim()));
    }

    @Override
    public Optional<PlatformUser> findById(long userId) {
        return Optional.ofNullable(byId.get(userId));
    }

    @Override
    public void save(PlatformUser user) {
        byId.put(user.id(), user);
        byUsername.put(user.username(), user);
    }

    /** seed 是否已写入。 */
    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
