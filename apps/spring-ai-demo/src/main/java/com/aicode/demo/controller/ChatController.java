package com.aicode.demo.controller;

import com.aicode.demo.application.ChatCommand;
import com.aicode.demo.application.ChatUseCase;
import com.aicode.demo.dto.ApiResponse;
import com.aicode.demo.dto.ChatRequest;
import com.aicode.demo.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天接入。只做校验与协议转换，不拼 Prompt、不调厂商 SDK。
 */
@RestController
@RequestMapping("/api/v1/chats")
public class ChatController {

    private final ChatUseCase chatUseCase;

    public ChatController(ChatUseCase chatUseCase) {
        this.chatUseCase = chatUseCase;
    }

    /**
     * 发起一轮聊天。
     *
     * @return 200 带 data；校验失败 400；模型失败 502
     */
    @PostMapping
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.of(ChatResponse.from(
                chatUseCase.chat(new ChatCommand(request.sessionId(), request.message()))
        ));
    }
}
