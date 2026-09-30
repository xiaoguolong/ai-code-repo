package com.aicode.framework.platform.infrastructure.eval;

import com.aicode.core.domain.model.EvalDataset;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 classpath {@code eval/*.json} 加载评估数据集。
 */
@Component
public class ClasspathEvalDatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(ClasspathEvalDatasetLoader.class);
    private static final String PATTERN = "classpath:eval/*.json";

    private final ObjectMapper objectMapper;

    public ClasspathEvalDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 加载全部 JSON 数据集。 */
    public List<EvalDataset> loadAll() {
        List<EvalDataset> datasets = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(PATTERN);
            for (Resource resource : resources) {
                datasets.add(loadResource(resource));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to scan eval datasets: " + PATTERN, ex);
        }
        return datasets;
    }

    private EvalDataset loadResource(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            EvalDatasetJson json = objectMapper.readValue(input, EvalDatasetJson.class);
            EvalDataset dataset = json.toDomain();
            log.info("[eval] loaded dataset key={} cases={} from {}",
                    dataset.datasetKey(), dataset.cases().size(), resource.getFilename());
            return dataset;
        }
    }
}
