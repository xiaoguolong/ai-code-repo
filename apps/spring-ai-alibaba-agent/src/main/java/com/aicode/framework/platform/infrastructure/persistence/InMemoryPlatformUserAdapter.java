package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内平台用户存储（Week 13；Week 28 加停用状态）。
 *
 * <p>{@link #save} 会保留已有 {@code disabledAt}，与 JDBC 适配器语义一致（seed 不能复活停用用户）。</p>
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
        PlatformUser existing = byId.get(user.id());
        PlatformUser merged = existing == null ? user : user.withDisabledAt(existing.disabledAt());
        if (existing != null && !existing.username().equals(merged.username())) {
            byUsername.remove(existing.username());
        }
        byId.put(merged.id(), merged);
        byUsername.put(merged.username(), merged);
    }

    @Override
    public List<PlatformUser> listAll() {
        return byId.values().stream()
                .sorted(Comparator.comparingLong(PlatformUser::id))
                .toList();
    }

    @Override
    public boolean updateDisabledAt(long userId, Instant disabledAt) {
        PlatformUser updated = byId.computeIfPresent(userId,
                (id, existing) -> existing.withDisabledAt(disabledAt));
        if (updated == null) {
            return false;
        }
        byUsername.put(updated.username(), updated);
        return true;
    }

    /** seed 是否已写入。 */
    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
