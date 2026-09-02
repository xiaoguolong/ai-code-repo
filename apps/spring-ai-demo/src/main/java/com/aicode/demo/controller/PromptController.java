package com.aicode.demo.controller;

import com.aicode.demo.application.ListPromptsUseCase;
import com.aicode.demo.dto.ApiResponse;
import com.aicode.demo.dto.PromptListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板列表。
 */
@RestController
@RequestMapping("/api/v1/prompts")
public class PromptController {

    private final ListPromptsUseCase listPromptsUseCase;

    public PromptController(ListPromptsUseCase listPromptsUseCase) {
        this.listPromptsUseCase = listPromptsUseCase;
    }

    /**
     * 列出当前版本所有模板。
     *
     * @return 200，data 为模板名与版本
     */
    @GetMapping
    public ApiResponse<PromptListResponse> list() {
        return ApiResponse.of(PromptListResponse.from(listPromptsUseCase.list()));
    }
}
