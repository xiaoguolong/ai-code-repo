package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformTenant;

import java.util.List;
import java.util.Optional;

/**
 * 租户持久化端口（Week 21）。
 *
 * <p>实现必须保证：{@link #save} 幂等（同一 tenantKey 覆盖而非报错），便于 seed 与迁移重复执行。</p>
 */
public interface PlatformTenantPort {

    /**
     * 按租户键查询。
     *
     * @param tenantKey 租户键
     * @return 命中租户，不存在为空
     */
    Optional<PlatformTenant> findByKey(String tenantKey);

    /**
     * 列出全部租户（管理员接口用）。
     *
     * @return 按租户键升序，无数据返回空列表，永不返回 null
     */
    List<PlatformTenant> listAll();

    /**
     * 保存（不存在插入，存在覆盖）。
     *
     * @param tenant 租户，tenantKey 非空
     */
    void save(PlatformTenant tenant);

    /**
     * 租户是否可用。未知租户视为不可用（白名单语义：没建就不让登）。
     *
     * @param tenantKey 租户键
     * @return 存在且状态为 ACTIVE 时 true
     */
    default boolean isActive(String tenantKey) {
        return findByKey(tenantKey).filter(PlatformTenant::active).isPresent();
    }

    /**
     * 无租户设施时的兜底实现：一律视为可用。
     *
     * <p>只用于 Week 13–20 的既有单测构造旧版 {@code PlatformAuthUseCase} 四参构造器；
     * 生产环境永远注入真实适配器（H2/PostgreSQL 都建了 {@code platform_tenant} 表）。</p>
     */
    PlatformTenantPort ALL_ACTIVE = new PlatformTenantPort() {
        @Override
        public Optional<PlatformTenant> findByKey(String tenantKey) {
            return Optional.empty();
        }

        @Override
        public List<PlatformTenant> listAll() {
            return List.of();
        }

        @Override
        public void save(PlatformTenant tenant) {
            // no-op：仅用于测试兜底
        }

        @Override
        public boolean isActive(String tenantKey) {
            return true;
        }
    };
}
