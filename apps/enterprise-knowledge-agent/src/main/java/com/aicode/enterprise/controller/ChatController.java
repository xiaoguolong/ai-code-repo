package com.aicode.enterprise.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.enterprise.application.AskKnowledgeUseCase;
import com.aicode.enterprise.application.KnowledgeQuestion;
import com.aicode.enterprise.application.ListChatHistoryUseCase;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.MessageDto;
import com.aicode.enterprise.dto.RagChatRequest;
import com.aicode.enterprise.dto.RagChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库问答与历史接入。只做校验、取当前用户与协议转换。
 */
@RestController
public class ChatController {

    private final AskKnowledgeUseCase askKnowledgeUseCase;
    private final ListChatHistoryUseCase listChatHistoryUseCase;

    public ChatController(AskKnowledgeUseCase askKnowledgeUseCase, ListChatHistoryUseCase listChatHistoryUseCase) {
        this.askKnowledgeUseCase = askKnowledgeUseCase;
        this.listChatHistoryUseCase = listChatHistoryUseCase;
    }

    /**
     * 基于某知识库回答一个问题。
     */
    @PostMapping("/api/v1/knowledge-bases/{kbId}/chats")
    public ApiResponse<RagChatResponse> ask(@PathVariable Long kbId, @Valid @RequestBody RagChatRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        KnowledgeQuestion question = new KnowledgeQuestion(kbId, userId, request.sessionId(), request.question(), request.topK());
        return ApiResponse.success(RagChatResponse.from(askKnowledgeUseCase.ask(question)));
    }

    /**
     * 查询会话消息历史。
     */
    @GetMapping("/api/v1/chats/{sessionId}/messages")
    public ApiResponse<List<MessageDto>> messages(@PathVariable String sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<MessageDto> data = listChatHistoryUseCase.list(userId, sessionId).stream()
                .map(MessageDto::from)
                .toList();
        return ApiResponse.success(data);
    }
}
