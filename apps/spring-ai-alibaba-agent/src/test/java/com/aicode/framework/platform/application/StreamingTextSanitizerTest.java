package com.aicode.framework.platform.application;

import com.aicode.core.infrastructure.config.GuardrailProperties;
import com.aicode.core.infrastructure.security.DefaultGuardrailAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式脱敏测试（Week 31）：PII 跨 chunk 也要被掩码，尾部必须 flush，关闭时原样透传。
 */
class StreamingTextSanitizerTest {

    @Test
    void masksPhoneNumberSplitAcrossChunks() {
        StreamingTextSanitizer sanitizer = sanitizer(true);
        StringBuilder out = new StringBuilder();
        for (String chunk : List.of("P".repeat(40), "138", "1234", "5678")) {
            out.append(sanitizer.accept(chunk));
        }
        out.append(sanitizer.flush());

        assertThat(out.toString()).contains("138****5678");
        assertThat(out.toString()).doesNotContain("13812345678");
    }

    @Test
    void masksIdCardSplitAcrossChunks() {
        StreamingTextSanitizer sanitizer = sanitizer(true);
        StringBuilder out = new StringBuilder();
        out.append(sanitizer.accept("Q".repeat(40)));
        out.append(sanitizer.accept("1101011990"));
        out.append(sanitizer.accept("01011234"));
        out.append(sanitizer.flush());

        assertThat(out.toString()).contains("110101********1234");
        assertThat(out.toString()).doesNotContain("110101199001011234");
    }

    @Test
    void keepsTailUntilFlush() {
        StreamingTextSanitizer sanitizer = sanitizer(true);

        assertThat(sanitizer.accept("短")).isEmpty();
        assertThat(sanitizer.flush()).isEqualTo("短");
    }

    @Test
    void passesThroughWhenGuardrailDisabled() {
        StreamingTextSanitizer sanitizer = sanitizer(false);
        StringBuilder out = new StringBuilder();
        out.append(sanitizer.accept("联系电话13812345678"));
        out.append(sanitizer.flush());

        assertThat(out.toString()).contains("13812345678");
    }

    private StreamingTextSanitizer sanitizer(boolean enabled) {
        DefaultGuardrailAdapter guardrail = new DefaultGuardrailAdapter(
                new GuardrailProperties(enabled, 8192, true, true, List.of()));
        return new StreamingTextSanitizer(new PlatformGuardrailService(guardrail), 2L, "knowledge-qa");
    }
}
