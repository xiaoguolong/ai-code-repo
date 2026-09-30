package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformRole;

import java.util.List;
import java.util.Optional;

/**
 * 平台角色持久化端口。
 */
public interface PlatformRolePort {

    Optional<PlatformRole> findByKey(String roleKey);

    List<PlatformRole> listAll();

    void save(PlatformRole role);
}
