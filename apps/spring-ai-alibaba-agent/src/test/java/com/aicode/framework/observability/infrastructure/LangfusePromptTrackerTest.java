package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.PromptReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 版本关联测试（Week 18）。
 *
 * <p>一个请求里可能先后加载多个 Prompt（例如「报告」与「随访」），所以不能靠"最近一次加载"猜测；
 * 这里用<b>内容全等</b>匹配：只有当前 chat 的 system 消息与某次加载的 Prompt 完全相同时才关联版本，
 * 不匹配就不挂（宁可不关联，也不产生错误归因）。</p>
 */
class LangfusePromptTrackerTest {

    private final LangfusePromptTracker tracker = new LangfusePromptTracker();

    @AfterEach
    void tearDown() {
        tracker.clear();
    }

    @Test
    void matchesLoadedPromptByExactContent() {
        tracker.recordLoaded("medical-report", "3", "你是医疗报告助手");
        tracker.recordLoaded("medical-followup", "7", "你是随访助手");

        assertThat(tracker.match("你是随访助手")).isEqualTo(new PromptReference("medical-followup", "7"));
        assertThat(tracker.match("你是医疗报告助手")).isEqualTo(new PromptReference("medical-report", "3"));
    }

    @Test
    void returnsNullWhenContentDiffersOrIsBlank() {
        tracker.recordLoaded("medical-report", "3", "你是医疗报告助手");

        assertThat(tracker.match("你是医疗报告助手（改了）")).isNull();
        assertThat(tracker.match("")).isNull();
        assertThat(tracker.match(null)).isNull();
    }

    @Test
    void returnsNullWhenNothingLoadedOrAfterClear() {
        assertThat(tracker.match("你是医疗报告助手")).isNull();

        tracker.recordLoaded("medical-report", "3", "你是医疗报告助手");
        tracker.clear();

        assertThat(tracker.match("你是医疗报告助手")).isNull();
    }

    @Test
    void ignoresEntriesWithoutNameOrVersion() {
        tracker.recordLoaded("", "3", "无名字");
        tracker.recordLoaded("medical-report", "", "无版本");

        assertThat(tracker.match("无名字")).isNull();
        assertThat(tracker.match("无版本")).isNull();
    }

    @Test
    void keepsOnlyBoundedNumberOfEntriesPerThread() {
        for (int i = 0; i < 40; i++) {
            tracker.recordLoaded("prompt-" + i, String.valueOf(i), "content-" + i);
        }

        assertThat(tracker.match("content-39")).isNotNull();
        assertThat(tracker.match("content-0")).isNull();
    }
}
