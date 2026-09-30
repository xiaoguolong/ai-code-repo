package com.aicode.framework.platform.domain.port;

import com.aicode.core.domain.model.EvalReport;

import java.util.List;
import java.util.Optional;

/**
 * 评估运行报告持久化端口。
 */
public interface EvalRunPort {

    void save(EvalReport report);

    Optional<EvalReport> findById(String runId);

    List<EvalReport> listByUserId(long userId);

    List<EvalReport> listAll();
}
