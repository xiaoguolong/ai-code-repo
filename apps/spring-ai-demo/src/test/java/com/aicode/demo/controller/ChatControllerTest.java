package com.aicode.demo.controller;

import com.aicode.demo.application.ChatCommand;
import com.aicode.demo.application.ChatOutcome;
import com.aicode.demo.application.ChatUseCase;
import com.aicode.demo.application.TokenStatsUseCase;
import com.aicode.demo.domain.exception.ChatModelException;
import com.aicode.demo.domain.model.TokenStats;
import com.aicode.demo.domain.model.TokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ChatController.class, TokenStatsController.class})
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatUseCase chatUseCase;

    @MockitoBean
    private TokenStatsUseCase tokenStatsUseCase;

    @Test
    void shouldRejectBlankMessage() throws Exception {
        mockMvc.perform(post("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void shouldRejectOversizedMessage() throws Exception {
        String tooLong = "a".repeat(8001);
        String body = objectMapper.writeValueAsString(new ChatRequestBody("s1", tooLong));

        mockMvc.perform(post("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void shouldReturnChatData() throws Exception {
        when(chatUseCase.chat(any(ChatCommand.class)))
                .thenReturn(new ChatOutcome("s1", "m1", "pong", new TokenUsage(8, 2, 10), "deepseek-chat"));

        mockMvc.perform(post("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"message\":\"ping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("s1"))
                .andExpect(jsonPath("$.data.messageId").value("m1"))
                .andExpect(jsonPath("$.data.content").value("pong"))
                .andExpect(jsonPath("$.data.usage.totalTokens").value(10));
    }

    @Test
    void shouldReturnBadGatewayWithoutStack_whenModelFails() throws Exception {
        when(chatUseCase.chat(any(ChatCommand.class))).thenThrow(new ChatModelException("timeout"));

        mockMvc.perform(post("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"ping\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("chat_model_error"))
                .andExpect(jsonPath("$.error.stack").doesNotExist());
    }

    @Test
    void shouldReturnTokenStats() throws Exception {
        when(tokenStatsUseCase.summary()).thenReturn(new TokenStats(3, 20, 10, 30));

        mockMvc.perform(get("/api/v1/token-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestCount").value(3))
                .andExpect(jsonPath("$.data.totalTokens").value(30));
    }

    @Test
    void shouldReturnJsonPayload_whenJsonFormat() throws Exception {
        java.util.Map<String, Object> payload = java.util.Map.of("answer", 42);
        when(chatUseCase.chat(any(ChatCommand.class)))
                .thenReturn(new ChatOutcome("s1", "m1", "{\"answer\":42}", new TokenUsage(8, 2, 10), "deepseek-chat", payload));

        mockMvc.perform(post("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"message\":\"count\",\"responseFormat\":\"json\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payload.answer").value(42));
    }

    @Test
    void shouldReturnUnprocessable_whenJsonInvalid() throws Exception {
        when(chatUseCase.chat(any(ChatCommand.class)))
                .thenThrow(new com.aicode.demo.domain.exception.StructuredOutputException("not valid JSON"));

        mockMvc.perform(post("/api/v1/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"message\":\"count\",\"responseFormat\":\"json\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("structured_output_error"));
    }

    private record ChatRequestBody(String sessionId, String message) {
    }
}
