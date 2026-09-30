package com.aicode.framework.platform.infrastructure.seed;

import com.aicode.core.domain.model.EvalDataset;
import com.aicode.framework.platform.domain.port.EvalDatasetPort;
import com.aicode.framework.platform.infrastructure.eval.ClasspathEvalDatasetLoader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时从 classpath 加载预置评估数据集。
 */
@Component
public class PlatformEvalSeedInitializer implements ApplicationRunner {

    private final EvalDatasetPort evalDatasetPort;
    private final ClasspathEvalDatasetLoader datasetLoader;

    public PlatformEvalSeedInitializer(EvalDatasetPort evalDatasetPort, ClasspathEvalDatasetLoader datasetLoader) {
        this.evalDatasetPort = evalDatasetPort;
        this.datasetLoader = datasetLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!evalDatasetPort.listAll().isEmpty()) {
            return;
        }
        for (EvalDataset dataset : datasetLoader.loadAll()) {
            evalDatasetPort.save(dataset);
        }
    }
}
