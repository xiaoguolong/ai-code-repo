package com.aicode.core.domain;

import com.aicode.core.domain.model.MemoryHit;
import com.aicode.core.domain.model.MemoryRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 召回记忆拼装测试：历史任务只作为 user 消息数据，禁止进入 system 提示。
 */
class MemoryContextAssemblerTest {

    @Test
    void includesRecalledTasksAndCurrentTaskInUserMessage() {
        MemoryRecord record = new MemoryRecord("m1", "s1", "评估患者 P001 血压", "血压偏高，建议随访", null);
        List<MemoryHit> hits = List.of(new MemoryHit(record, 0.9));

        String message = MemoryContextAssembler.buildUserMessage(hits, "那血糖呢？");

        assertThat(message).contains("评估患者 P001 血压");
        assertThat(message).contains("血压偏高，建议随访");
        assertThat(message).contains("那血糖呢？");
    }

    @Test
    void returnsRawTaskWhenNoHits() {
        assertThat(MemoryContextAssembler.buildUserMessage(List.of(), "当前任务")).isEqualTo("当前任务");
    }
}
