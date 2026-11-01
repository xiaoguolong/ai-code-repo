package com.aicode.enterprise.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 清洗输出兜底测试：去掉模型写进正文的"思考过程"前缀。
 */
class IngestPipelineSanitizeTest {

    @Test
    void stripsReasoningBeforeFirstHeading() {
        String raw = "我们需要回答用户。\n用户要求清洗 OCR 文本。\n## 一、普通感冒\n正文内容";
        assertThat(IngestPipeline.sanitizeCleanOutput(raw, "fallback"))
                .isEqualTo("## 一、普通感冒\n正文内容");
    }

    @Test
    void fallsBackToOriginalWhenNoHeading() {
        assertThat(IngestPipeline.sanitizeCleanOutput("我们需要回答用户，但没有标题", "原始片段"))
                .isEqualTo("原始片段");
    }

    @Test
    void keepsNormalMarkdownUntouched() {
        assertThat(IngestPipeline.sanitizeCleanOutput("## 标题\n正文", "fallback"))
                .isEqualTo("## 标题\n正文");
    }
}
