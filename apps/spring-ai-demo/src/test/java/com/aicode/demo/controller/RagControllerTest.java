package com.aicode.demo.controller;

import com.aicode.demo.application.AskKnowledgeUseCase;
import com.aicode.demo.application.KnowledgeAnswer;
import com.aicode.demo.application.KnowledgeQuestion;
import com.aicode.demo.domain.model.VectorSearchHit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RAG 问答接口。
 */
@WebMvcTest(controllers = RagController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AskKnowledgeUseCase askKnowledgeUseCase;

    @Test
    void shouldRejectBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/rag/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnAnswerWithSources() throws Exception {
        when(askKnowledgeUseCase.ask(any(KnowledgeQuestion.class)))
                .thenReturn(new KnowledgeAnswer("员工提交申请后由主管审批。", List.of(
                        new VectorSearchHit("doc-1", 0, "请假流程：员工提交申请后由主管审批。", 0.95)
                )));

        mockMvc.perform(post("/api/v1/rag/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"公司请假流程\",\"topK\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.answer").value("员工提交申请后由主管审批。"))
                .andExpect(jsonPath("$.data.sources[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.data.sources[0].score").value(0.95));
    }
}
