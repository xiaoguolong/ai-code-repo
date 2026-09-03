package com.aicode.demo.controller;

import com.aicode.demo.application.ListMessagesUseCase;
import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.MessageRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListMessagesUseCase listMessagesUseCase;

    @Test
    void shouldReturnMessagesForSession() throws Exception {
        when(listMessagesUseCase.list("s1")).thenReturn(List.of(
                new ChatMessage(MessageRole.USER, "hi"),
                new ChatMessage(MessageRole.ASSISTANT, "hello")
        ));

        mockMvc.perform(get("/api/v1/chats/s1/messages").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("hi"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"));
    }
}
