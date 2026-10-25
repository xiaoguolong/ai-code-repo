package com.aicode.enterprise.support;

import com.aicode.enterprise.domain.model.SsoUserLink;
import com.aicode.enterprise.domain.port.SsoUserLinkPort;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 测试用内存映射表：platformUserId → SsoUserLink。 */
public class InMemorySsoUserLinkAdapter implements SsoUserLinkPort {

    private final ConcurrentHashMap<Long, SsoUserLink> links = new ConcurrentHashMap<>();

    @Override
    public Optional<SsoUserLink> findByPlatformUserId(long platformUserId) {
        return Optional.ofNullable(links.get(platformUserId));
    }

    @Override
    public Optional<SsoUserLink> findByEnterpriseUserId(long enterpriseUserId) {
        return links.values().stream()
                .filter(link -> link.enterpriseUserId() == enterpriseUserId)
                .findFirst();
    }

    @Override
    public List<SsoUserLink> listAll(int limit) {
        return links.values().stream()
                .sorted(Comparator.comparing(SsoUserLink::linkedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Override
    public void link(long platformUserId, long enterpriseUserId, String tenantKey) {
        links.put(platformUserId, new SsoUserLink(
                platformUserId, enterpriseUserId, tenantKey, Instant.now()));
    }

    @Override
    public boolean unlink(long platformUserId) {
        return links.remove(platformUserId) != null;
    }

    /** 供断言使用。 */
    public int size() {
        return links.size();
    }
}
