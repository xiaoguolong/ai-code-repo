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

    @Test
    void fallsBackWhenCleanedTextIsTooShort() {
        assertThat(IngestPipeline.acceptCleaned("嗯", "这是一段足够长的原始 OCR 文本，用于兜底。"))
                .isEqualTo("这是一段足够长的原始 OCR 文本，用于兜底。");
    }

    @Test
    void fallsBackWhenModelEchoesInstruction() {
        String echo = "用户要求清洗文本，只输出清洗后的 markdown 正文，以下是结果：## 标题\n正文";
        assertThat(IngestPipeline.acceptCleaned(echo, "原始片段原始片段原始片段原始片段"))
                .isEqualTo("原始片段原始片段原始片段原始片段");
    }

    @Test
    void acceptsNormalCleanedText() {
        String cleaned = "## 一、普通感冒\n\n普通感冒多为自限性疾病，病程 7-10 天。";
        assertThat(IngestPipeline.acceptCleaned(cleaned, "原始片段原始片段原始片段原始片段"))
                .isEqualTo(cleaned);
    }
}
