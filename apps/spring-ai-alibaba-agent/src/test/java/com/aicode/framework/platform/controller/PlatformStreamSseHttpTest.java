package com.aicode.framework.platform.controller;

import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.analysis.application.PatientAnalysisUseCase;
import com.aicode.framework.analysis.domain.model.PatientAnalysisResult;
import com.aicode.framework.workflow.domain.model.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE 主链路契约（Week 31）：controller → dispatcher → runner → agent → 事件。
 *
 * <p>用 Mockito 固定患者分析结果与增量，验证真正被写出的事件序列与终态；
 * 真实模型/首字耗时由云端 `cloud-accept-week31.sh` 验证。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.security.web-interceptors.enabled=true",
        "platform.persistence.mode=jdbc",
        "platform.memory.lifecycle.cleanup-enabled=false",
        "observability.enabled=false",
        "langfuse.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformStreamSseHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PatientAnalysisUseCase patientAnalysisUseCase;

    @BeforeEach
    void stubStreamingAnalysis() {
        when(patientAnalysisUseCase.run(eq("P001"), any(), any(), any(ChatStreamListener.class)))
                .thenAnswer(invocation -> {
                    ChatStreamListener listener = invocation.getArgument(3);
                    listener.onDelta("报告");
                    listener.onDelta("正文");
                    return new PatientAnalysisResult(
                            "P001", null, null, List.of(), RiskLevel.LOW, "规则判定低风险",
                            "报告正文", false, null, List.of(),
                            new TokenUsage(3, 4, 7), "test-model");
                });
    }

    @Test
    void streamsMetaDeltasAndFinalOverSse() throws Exception {
        String token = login("admin", "admin123");

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/platform/agents/patient-analysis/runs/stream")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":{\"patientId\":\"P001\"}}"))
                .andExpect(status().isOk())
                .andReturn();

        // 同步执行 + 逐帧 flush：请求返回时终态已写完。
        String body = mvcResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body).contains("event:meta");
        assertThat(body).contains("event:delta");
        assertThat(body).contains("event:final");
        assertThat(body).contains("COMPLETED");
        assertThat(body).contains("报告正文");
        assertThat(body).doesNotContain("event:error");

        String finalData = body.lines()
                .filter(line -> line.startsWith("data:"))
                .reduce((first, second) -> second)
                .orElseThrow();
        var envelope = objectMapper.readTree(finalData.substring("data:".length()));
        assertThat(envelope.path("traceId").asText()).isNotBlank();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
