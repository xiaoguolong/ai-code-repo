package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformUser;

import java.util.Optional;

/**
 * 平台用户持久化端口。
 */
public interface PlatformUserPort {

    Optional<PlatformUser> findByUsername(String username);

    Optional<PlatformUser> findById(long userId);

    void save(PlatformUser user);
}
