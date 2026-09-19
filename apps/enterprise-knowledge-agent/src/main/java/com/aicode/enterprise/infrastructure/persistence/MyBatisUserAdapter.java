package com.aicode.enterprise.infrastructure.persistence;

import com.aicode.core.domain.model.User;
import com.aicode.enterprise.domain.port.UserPort;
import com.aicode.enterprise.infrastructure.persistence.entity.AppUserEntity;
import com.aicode.enterprise.infrastructure.persistence.mapper.AppUserMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 基于 Fluent-MyBatis 的用户持久化适配器。
 */
@Component
public class MyBatisUserAdapter implements UserPort {

    private final AppUserMapper userMapper;

    public MyBatisUserAdapter(AppUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public User create(User user) {
        AppUserEntity entity = new AppUserEntity(user.username(), user.passwordHash(), user.createdAt());
        userMapper.insert(entity);
        return new User(entity.getId(), entity.getUsername(), entity.getPasswordHash(), entity.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userMapper.listByMap(false, Map.of("username", username)).stream()
                .findFirst()
                .map(this::toModel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(long id) {
        return userMapper.listByMap(false, Map.of("id", id)).stream()
                .findFirst()
                .map(this::toModel);
    }

    private User toModel(AppUserEntity entity) {
        return new User(entity.getId(), entity.getUsername(), entity.getPasswordHash(), entity.getCreatedAt());
    }
}
