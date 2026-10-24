package com.aicode.enterprise.support;

import com.aicode.enterprise.domain.port.SsoUserLinkPort;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 测试用内存映射表：platformUserId → enterpriseUserId。 */
public class InMemorySsoUserLinkAdapter implements SsoUserLinkPort {

    private final ConcurrentHashMap<Long, Long> links = new ConcurrentHashMap<>();

    @Override
    public Optional<Long> findEnterpriseUserId(long platformUserId) {
        return Optional.ofNullable(links.get(platformUserId));
    }

    @Override
    public void link(long platformUserId, long enterpriseUserId, String tenantKey) {
        links.put(platformUserId, enterpriseUserId);
    }

    /** 供断言使用。 */
    public int size() {
        return links.size();
    }
}
