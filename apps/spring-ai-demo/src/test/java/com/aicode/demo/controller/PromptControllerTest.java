package com.aicode.demo.controller;

import com.aicode.demo.application.ListPromptsUseCase;
import com.aicode.demo.domain.model.PromptDescriptor;
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

@WebMvcTest(PromptController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class PromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListPromptsUseCase listPromptsUseCase;

    @Test
    void shouldReturnPromptList() throws Exception {
        when(listPromptsUseCase.list()).thenReturn(List.of(
                new PromptDescriptor("system", "v1"),
                new PromptDescriptor("json", "v1")
        ));

        mockMvc.perform(get("/api/v1/prompts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prompts[0].name").value("system"))
                .andExpect(jsonPath("$.data.prompts[1].name").value("json"));
    }
}
