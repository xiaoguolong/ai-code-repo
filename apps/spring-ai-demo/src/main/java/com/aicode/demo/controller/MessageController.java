package com.aicode.demo.controller;

import com.aicode.demo.application.ListMessagesUseCase;
import com.aicode.demo.dto.ApiResponse;
import com.aicode.demo.dto.ChatMessageDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话历史消息查询。
 */
@RestController
@RequestMapping("/api/v1/chats/{sessionId}/messages")
public class MessageController {

    private final ListMessagesUseCase listMessagesUseCase;

    public MessageController(ListMessagesUseCase listMessagesUseCase) {
        this.listMessagesUseCase = listMessagesUseCase;
    }

    /**
     * 列出会话历史消息。
     *
     * @param sessionId 会话标识
     * @return 200，data 为消息列表
     */
    @GetMapping
    public ApiResponse<List<ChatMessageDto>> list(@PathVariable String sessionId) {
        List<ChatMessageDto> messages = listMessagesUseCase.list(sessionId).stream()
                .map(ChatMessageDto::from)
                .toList();
        return ApiResponse.of(messages);
    }
}
