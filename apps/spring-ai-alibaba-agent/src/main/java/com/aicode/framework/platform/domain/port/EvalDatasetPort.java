package com.aicode.framework.platform.domain.port;

import com.aicode.core.domain.model.EvalDataset;

import java.util.List;
import java.util.Optional;

/**
 * 评估数据集持久化端口。
 */
public interface EvalDatasetPort {

    void save(EvalDataset dataset);

    Optional<EvalDataset> findByKey(String datasetKey);

    List<EvalDataset> listAll();
}
