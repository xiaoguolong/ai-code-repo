package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.ExecutionRecord;

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
}
