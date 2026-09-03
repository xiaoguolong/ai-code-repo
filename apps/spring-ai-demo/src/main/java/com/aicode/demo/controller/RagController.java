package com.aicode.demo.controller;

import com.aicode.demo.application.AskKnowledgeUseCase;
import com.aicode.demo.application.KnowledgeQuestion;
import com.aicode.demo.dto.ApiResponse;
import com.aicode.demo.dto.RagChatRequest;
import com.aicode.demo.dto.RagChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答接入。只做校验与协议转换，检索编排在用例层。
 */
@RestController
@RequestMapping("/api/v1/rag/chats")
public class RagController {

    private final AskKnowledgeUseCase askKnowledgeUseCase;

    public RagController(AskKnowledgeUseCase askKnowledgeUseCase) {
        this.askKnowledgeUseCase = askKnowledgeUseCase;
    }

    /**
     * 基于知识库回答问题。
     *
     * @return 200 带 answer 与 sources；校验失败 400
     */
    @PostMapping
    public ApiResponse<RagChatResponse> ask(@Valid @RequestBody RagChatRequest request) {
        KnowledgeQuestion question = new KnowledgeQuestion(request.question(), request.topK());
        return ApiResponse.success(RagChatResponse.from(askKnowledgeUseCase.ask(question)));
    }
}
