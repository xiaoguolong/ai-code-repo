package com.aicode.enterprise.support;

import com.aicode.core.domain.model.User;
import com.aicode.enterprise.domain.port.UserPort;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 测试用内存用户仓储：自动分配主键，语义与生产适配器一致（用户名唯一）。 */
public class InMemoryUserPort implements UserPort {

    private final ConcurrentHashMap<Long, User> byId = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(100);

    @Override
    public User create(User user) {
        long id = user.id() == null ? sequence.incrementAndGet() : user.id();
        User stored = new User(id, user.username(), user.passwordHash(), user.createdAt());
        byId.put(id, stored);
        return stored;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return byId.values().stream()
                .filter(user -> user.username().equals(username))
                .findFirst();
    }

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** 供测试预置已存在用户。 */
    public User save(User user) {
        return create(user);
    }
}
