package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.ExecutionRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 执行记录端口。
 */
public interface ExecutionRecordPort {

    void save(ExecutionRecord record);

    Optional<ExecutionRecord> findById(String executionId);

    List<ExecutionRecord> listAll();

    List<ExecutionRecord> listByUserId(long userId);

    /**
     * 查询某租户在时间窗内的执行记录（Week 24：运营日报按租户统计用）。
     *
     * @param tenantKey 租户键
     * @param from      起始时间（含）
     * @param to        结束时间（不含）
     * @param limit     条数上限，必须为正
     * @return 按开始时间倒序的记录，无数据返回空列表
     */
    List<ExecutionRecord> listByTenantBetween(String tenantKey, Instant from, Instant to, int limit);

    /**
     * 查询某用户在时间窗内的执行记录（Week 24：非 admin 只能统计自己的执行）。
     *
     * @param userId 用户 ID
     * @param from   起始时间（含）
     * @param to     结束时间（不含）
     * @param limit  条数上限，必须为正
     * @return 按开始时间倒序的记录，无数据返回空列表
     */
    List<ExecutionRecord> listByUserBetween(long userId, Instant from, Instant to, int limit);
}
