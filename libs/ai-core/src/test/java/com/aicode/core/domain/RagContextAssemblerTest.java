package com.aicode.core.domain;

import com.aicode.core.domain.model.VectorSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 上下文组装单元测试。
 */
class RagContextAssemblerTest {

    @Test
    void buildsMessageWithEmptyHits() {
        String message = RagContextAssembler.buildUserMessage(List.of(), "问题？");

        assertThat(message).contains("参考资料：（无）").contains("问题：问题？");
    }

    @Test
    void buildsMessageWithHitsAndQuestion() {
        VectorSearchHit hit = new VectorSearchHit("doc-1", 1L, 0, "片段内容", 0.9);
        String message = RagContextAssembler.buildUserMessage(List.of(hit), "问题？");

        assertThat(message).contains("片段内容").contains("问题：问题？").contains("[片段 1]");
    }
}
